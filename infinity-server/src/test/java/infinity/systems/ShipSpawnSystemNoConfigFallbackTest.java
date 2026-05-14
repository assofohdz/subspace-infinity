// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.BombLevel;
import infinity.BulletLevel;
import infinity.Ship;
import infinity.config.BombStats;
import infinity.config.BulletStats;
import infinity.config.MineStats;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Energy;
import infinity.es.ship.ShipType;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustStats;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.systems.ship.ShipSpawnSystem;
import org.junit.Test;

/** Slice 4 of the spawn-projection test harness — `cfg == null` branch in {@link ShipSpawnSystem}: no projection runs, prior component values survive. See PRD `.scratch/spawn-projection-test-harness/PRD.md`. */
public class ShipSpawnSystemNoConfigFallbackTest {

  private static final String UNREGISTERED_ARENA = "unregistered";

  /**
   * A ship spawned into an arena that {@link ConfigRegistrySystem#replace} was
   * never called for hits the {@code cfg == null} branch in
   * {@link ShipSpawnSystem#applyConfigTo} — the system logs the warning and
   * returns without writing any of the ~30 projected components. The ship
   * therefore has no Thrust/Speed/Energy/etc. assigned (DefaultEntityData
   * returns {@code null} for absent components).
   */
  @Test
  public void freshShip_inUnregisteredArena_skipsProjection() {
    final Fixture f = newFixture();
    try {
      // Note: NO registry.replace(...) call — arena is genuinely unknown.
      final ArenaId arenaId = new ArenaId(UNREGISTERED_ARENA, EntityId.NULL_ID);

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, arenaId);

      f.systems.update();

      // No projection ran — none of the ship-stat or weapon-stat components
      // exist on the entity.
      assertNull(
          "Thrust must not be projected when ship's arena has no config",
          f.ed.getComponent(ship, Thrust.class));
      assertNull(
          "ThrustStats must not be projected when ship's arena has no config",
          f.ed.getComponent(ship, ThrustStats.class));
      assertNull(
          "Energy must not be projected when ship's arena has no config",
          f.ed.getComponent(ship, Energy.class));
      assertNull(
          "BombStats must not be projected when ship's arena has no config",
          f.ed.getComponent(ship, infinity.es.ship.weapons.BombStats.class));
      // Identity components the test stamped itself remain — projection is
      // skipped, not the entity destroyed.
      assertEquals(Ship.WARBIRD, f.ed.getComponent(ship, ShipType.class).getType());
      assertEquals(UNREGISTERED_ARENA, f.ed.getComponent(ship, ArenaId.class).getArena());
    } finally {
      f.shutdown();
    }
  }

  /**
   * The PRD's "retains its prior component values" case: a ship is fully
   * projected from arena A, then re-stamped into arena B which has no entry
   * in the registry. The {@code ArenaId} rewrite surfaces in
   * {@code getChangedEntities()} → {@code applyConfigTo} → {@code cfg == null}
   * → early return. The components arena A wrote survive untouched, mirroring
   * the comment in {@code warnMissingShipConfig}: "leaving defaults".
   */
  @Test
  public void shipMovedFromConfiguredToUnregisteredArena_retainsPriorComponents() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaA = new ArenaId("configured", EntityId.NULL_ID);
      final ArenaId arenaB = new ArenaId(UNREGISTERED_ARENA, EntityId.NULL_ID);
      f.registry.replace(arenaA, snapshotWith(warbird()));
      // Note: no replace(arenaB, ...) — arena B is unknown.

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, arenaA);
      f.systems.update();

      // Sanity — arena-A snapshot landed.
      assertEquals(16, f.ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(1000, f.ed.getComponent(ship, Energy.class).getEnergy());
      assertNotNull(f.ed.getComponent(ship, infinity.es.ship.weapons.BombStats.class));

      // Move the ship to the unregistered arena. The change branch fires and
      // hits the no-config fallback.
      f.ed.setComponent(ship, arenaB);
      f.systems.update();

      // Arena membership updated; projected components survived because the
      // fallback no-ops.
      assertEquals(
          "ArenaId rewrite landed even though projection was skipped",
          UNREGISTERED_ARENA,
          f.ed.getComponent(ship, ArenaId.class).getArena());
      assertEquals(
          "Thrust retained from arena A's projection (no-config fallback no-ops)",
          16,
          f.ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "ThrustStats retained from arena A",
          19,
          f.ed.getComponent(ship, ThrustStats.class).max());
      assertEquals(
          "Energy retained from arena A",
          1000,
          f.ed.getComponent(ship, Energy.class).getEnergy());
      assertNotNull(
          "BombStats retained from arena A — projection was skipped, not undone",
          f.ed.getComponent(ship, infinity.es.ship.weapons.BombStats.class));
    } finally {
      f.shutdown();
    }
  }

  /**
   * Same fallback contract from a different angle: the registry has a
   * snapshot installed for the arena, but the snapshot has no entry for the
   * ship's specific {@link ShipType} (e.g. only WARBIRD configured, JAVELIN
   * spawned). {@code ShipConfig getShip(JAVELIN)} returns {@code null} →
   * same {@code cfg == null} branch as the unregistered-arena case.
   */
  @Test
  public void shipTypeNotInSnapshot_skipsProjection() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaId = new ArenaId("warbirdOnly", EntityId.NULL_ID);
      // Snapshot configures WARBIRD; JAVELIN is intentionally absent.
      f.registry.replace(arenaId, snapshotWith(warbird()));

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.JAVELIN));
      f.ed.setComponent(ship, arenaId);

      f.systems.update();

      assertNull(
          "Thrust must not be projected when ShipType is missing from snapshot",
          f.ed.getComponent(ship, Thrust.class));
      assertNull(
          "Energy must not be projected when ShipType is missing from snapshot",
          f.ed.getComponent(ship, Energy.class));
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────
  // Fixtures
  // ──────────────────────────────────────────────────────────────────

  /** Minimal WARBIRD template — only the fields the assertions read above. */
  private static ShipConfig warbird() {
    return new ShipConfig(
        Ship.WARBIRD,
        new ShipStat(210, 300, 40),                                       // rotation
        new ShipStat(16, 19, 2),                                          // thrust
        new ShipStat(2010, 3250, 250),                                    // speed
        new ShipStat(400, 1150, 166),                                     // recharge
        new ShipStat(1000, 1700, 100),                                    // energy
        0.99,                                                             // linearDamping
        8.0,                                                              // turnResponsiveness
        1.0,                                                              // bounceRestitution
        250.0,                                                            // radarRange
        new BombStats(BombLevel.BOMB_1, BombLevel.BOMB_4, 10, 25L, 2000, 400),
        null,                                                             // gravBombs
        new BulletStats(BulletLevel.LEVEL_1, BulletLevel.LEVEL_4, 10, 25L, 2000),
        new MineStats(BombLevel.BOMB_1, BombLevel.BOMB_4, 50, 500L, 0),
        null, null, null, null, null, null, null, null, null, null, null,
        true);
  }

  private static ConfigRegistry snapshotWith(final ShipConfig warbird) {
    return ConfigRegistry.builder().ship(Ship.WARBIRD, warbird).build();
  }

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    final ConfigRegistrySystem registry =
        systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    // ShipSpawnSystem registered for its side effect (the system under test)
    // — these slices don't call reprojectAll() so we don't keep the handle.
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed, registry);
  }

  private static final class Fixture {
    private final GameSystemManager systems;
    private final DefaultEntityData ed;
    private final ConfigRegistrySystem registry;

    private Fixture(
        final GameSystemManager systems,
        final DefaultEntityData ed,
        final ConfigRegistrySystem registry) {
      this.systems = systems;
      this.ed = ed;
      this.registry = registry;
    }

    private void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
