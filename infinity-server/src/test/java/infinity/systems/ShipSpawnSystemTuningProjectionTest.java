// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.BombLevel;
import infinity.BulletLevel;
import infinity.Ship;
import infinity.config.BombStats;
import infinity.config.BulletStats;
import infinity.config.CountStats;
import infinity.config.CountWithDelayStats;
import infinity.config.MineStats;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.ShipType;
import infinity.es.ship.ThrustStats;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelStats;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorStats;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.systems.ship.ShipSpawnSystem;
import org.junit.Test;

/** Slice 2 of the spawn-projection test harness — tuning re-project preserves live pools + current counts; only capability stats / *Max are rewritten. See PRD `.scratch/spawn-projection-test-harness/PRD.md`. */
public class ShipSpawnSystemTuningProjectionTest {

  private static final String TEST_ARENA_NAME = "test";

  /**
   * Damage + ammo-burn the ship, then trigger {@link ShipSpawnSystem#reprojectAll}
   * with a richer snapshot. Live Energy + every Continuous-half count
   * (Bomb/Bullet/Mine/Burst/Thor/Repel current) survives; the matching {@code *Stats}
   * records pick up the new caps.
   */
  @Test
  public void reprojectAll_preservesLivePoolAndCurrentCounts_updatesCapabilityStats() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaId = new ArenaId(TEST_ARENA_NAME, EntityId.NULL_ID);
      f.registry.replace(arenaId, snapshotWith(warbird(/* tier */ 'A')));

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, arenaId);

      // Tick 1: respawn projection seeds Continuous + Stats from snapshot A.
      f.systems.update();
      // Sanity — spawn projected the start values.
      assertEquals(1000, f.ed.getComponent(ship, Energy.class).getEnergy());
      assertEquals(BombLevel.BOMB_1, f.ed.getComponent(ship, BombCurrentLevel.class).getLevel());
      assertEquals(BulletLevel.LEVEL_1, f.ed.getComponent(ship, BulletCurrentLevel.class).getLevel());
      assertEquals(BombLevel.BOMB_1, f.ed.getComponent(ship, MineCurrentLevel.class).getLevel());
      assertEquals(5, f.ed.getComponent(ship, Burst.class).getCount());
      assertEquals(2, f.ed.getComponent(ship, ThorCurrentCount.class).getCount());
      assertEquals(10, f.ed.getComponent(ship, Repel.class).getCount());

      // Simulate gameplay — damage + ammo-burn. Components are immutable; build new instances.
      f.ed.setComponent(ship, new Energy(250));
      f.ed.setComponent(ship, new BombCurrentLevel(BombLevel.BOMB_3));
      f.ed.setComponent(ship, new BulletCurrentLevel(BulletLevel.LEVEL_2));
      f.ed.setComponent(ship, new MineCurrentLevel(BombLevel.BOMB_2));
      f.ed.setComponent(ship, new Burst(2));
      f.ed.setComponent(ship, new ThorCurrentCount(1));
      f.ed.setComponent(ship, new Repel(4));

      // Hot-reload snapshot to one with strictly larger caps + thrust/speed bumps so a
      // mistaken respawn-projection would visibly stomp the gameplay state above.
      f.registry.replace(arenaId, snapshotWith(warbird(/* tier */ 'B')));
      final int reprojected = f.spawnSystem.reprojectAll();

      assertEquals("reprojectAll touched the only live ship", 1, reprojected);

      // Live pools — Energy, weapon-level current, inventory current — all preserved.
      assertEquals(
          "Energy pool preserved across tuning reproject",
          250,
          f.ed.getComponent(ship, Energy.class).getEnergy());
      assertEquals(
          "BombCurrentLevel preserved across tuning reproject",
          BombLevel.BOMB_3,
          f.ed.getComponent(ship, BombCurrentLevel.class).getLevel());
      assertEquals(
          "BulletCurrentLevel preserved across tuning reproject",
          BulletLevel.LEVEL_2,
          f.ed.getComponent(ship, BulletCurrentLevel.class).getLevel());
      assertEquals(
          "MineCurrentLevel preserved across tuning reproject",
          BombLevel.BOMB_2,
          f.ed.getComponent(ship, MineCurrentLevel.class).getLevel());
      assertEquals(
          "Burst count preserved across tuning reproject",
          2,
          f.ed.getComponent(ship, Burst.class).getCount());
      assertEquals(
          "ThorCurrentCount preserved across tuning reproject",
          1,
          f.ed.getComponent(ship, ThorCurrentCount.class).getCount());
      assertEquals(
          "Repel count preserved across tuning reproject",
          4,
          f.ed.getComponent(ship, Repel.class).getCount());

      // Capability stats — *Stats / *Max — pick up snapshot B's values.
      assertEquals(
          "ThrustStats.max picks up snapshot B's cap",
          30,
          f.ed.getComponent(ship, ThrustStats.class).max());
      // EnergyStats.hardMax (== ShipConfig.energy.max()) updates on tuning reload.
      assertEquals(
          "EnergyStats.hardMax picks up snapshot B's hard cap",
          2400,
          f.ed.getComponent(ship, EnergyStats.class).hardMax());
      assertEquals(
          "BombStats.max picks up snapshot B's cap",
          BombLevel.BOMB_4,
          f.ed.getComponent(ship, infinity.es.ship.weapons.BombStats.class).max());
      assertEquals(
          "BulletStats.max picks up snapshot B's cap",
          BulletLevel.LEVEL_4,
          f.ed.getComponent(ship, infinity.es.ship.weapons.BulletStats.class).max());
      assertEquals(
          "MineStats.max picks up snapshot B's cap",
          BombLevel.BOMB_4,
          f.ed.getComponent(ship, infinity.es.ship.weapons.MineStats.class).max());
      assertEquals(
          "BurstStats.max picks up snapshot B's cap",
          8,
          f.ed.getComponent(ship, infinity.es.ship.actions.BurstStats.class).max());
      assertEquals(
          "ThorStats.max picks up snapshot B's cap",
          5,
          f.ed.getComponent(ship, ThorStats.class).max());
      assertEquals(
          "RepelStats.max picks up snapshot B's cap",
          30,
          f.ed.getComponent(ship, RepelStats.class).max());
    } finally {
      f.shutdown();
    }
  }

  /**
   * The other tuning-projection trigger: an {@link ArenaId} re-set surfaces the ship in
   * {@code ships.getChangedEntities()}; without a {@code ResetLivePool} marker the system
   * applies projection with {@code resetLivePool=false}. Same preserve-live-pool +
   * preserve-current-counts contract as the {@code reprojectAll} path.
   */
  @Test
  public void arenaIdRewrite_withoutResetLivePool_preservesLivePoolAndCurrentCounts() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaA = new ArenaId(TEST_ARENA_NAME, EntityId.NULL_ID);
      final ArenaId arenaB = new ArenaId("other", EntityId.NULL_ID);
      // Both arenas know about WARBIRD but with different snapshots — moving the ship
      // from A to B simulates an arena cross + per-arena tuning difference.
      f.registry.replace(arenaA, snapshotWith(warbird(/* tier */ 'A')));
      f.registry.replace(arenaB, snapshotWith(warbird(/* tier */ 'B')));

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, arenaA);

      // Tick 1: respawn projection from arena A.
      f.systems.update();

      // Damage + ammo-burn against the arena-A baseline.
      f.ed.setComponent(ship, new Energy(300));
      f.ed.setComponent(ship, new BombCurrentLevel(BombLevel.BOMB_2));
      f.ed.setComponent(ship, new Burst(3));
      f.ed.setComponent(ship, new Repel(7));

      // Move the ship to arena B without stamping ResetLivePool — this is the
      // "ArenaId rewrite is a tuning event" branch (the ship-swap path stamps
      // ResetLivePool to flip the branch back to respawn semantics; that path
      // is covered by ShipSpawnSystemTest.shipSwap_resetLivePoolMarker_*).
      f.ed.setComponent(ship, arenaB);
      f.systems.update();

      // Live pools / current counts preserved (no ResetLivePool marker).
      assertEquals(
          "Energy pool preserved across arena rewrite (tuning, not respawn)",
          300,
          f.ed.getComponent(ship, Energy.class).getEnergy());
      assertEquals(
          "BombCurrentLevel preserved across arena rewrite",
          BombLevel.BOMB_2,
          f.ed.getComponent(ship, BombCurrentLevel.class).getLevel());
      assertEquals(
          "Burst count preserved across arena rewrite",
          3,
          f.ed.getComponent(ship, Burst.class).getCount());
      assertEquals(
          "Repel count preserved across arena rewrite",
          7,
          f.ed.getComponent(ship, Repel.class).getCount());

      // Capability stats now reflect arena B.
      assertEquals(
          "ThrustStats.max picks up arena B's cap after rewrite",
          30,
          f.ed.getComponent(ship, ThrustStats.class).max());
      assertEquals(
          "BombStats.max picks up arena B's cap after rewrite",
          BombLevel.BOMB_4,
          f.ed.getComponent(ship, infinity.es.ship.weapons.BombStats.class).max());
      assertEquals(
          "RepelStats.max picks up arena B's cap after rewrite",
          30,
          f.ed.getComponent(ship, RepelStats.class).max());
      assertNotNull(
          "ThorStats projected from arena B even though arena A didn't change",
          f.ed.getComponent(ship, ThorStats.class));
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────
  // Fixtures
  // ──────────────────────────────────────────────────────────────────

  /**
   * Tier 'A' (baseline) vs tier 'B' (richer) WARBIRD templates. Every tunable that the
   * tests assert against differs between the two so a regression that silently
   * stomped a value would show up in at least one assertion regardless of which one
   * matched the baseline.
   */
  @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity"}) // 2-tier ternary table preserves side-by-side A/B diff visibility per test design
  private static ShipConfig warbird(final char tier) {
    final boolean isB = tier == 'B';
    final int thrustInitial = isB ? 24 : 16;
    final int thrustMax = isB ? 30 : 19;
    final int speedInitial = isB ? 2500 : 2010;
    final int speedMax = isB ? 5000 : 3250;
    final int energyInitial = isB ? 1100 : 1000;
    final int energyMax = isB ? 2400 : 1700;
    final BombLevel bombMax = isB ? BombLevel.BOMB_4 : BombLevel.BOMB_2;
    final BulletLevel bulletMax = isB ? BulletLevel.LEVEL_4 : BulletLevel.LEVEL_2;
    final BombLevel mineMax = isB ? BombLevel.BOMB_4 : BombLevel.BOMB_2;
    final int burstMax = isB ? 8 : 5;
    final int thorMax = isB ? 5 : 2;
    final int repelMax = isB ? 30 : 20;
    return new ShipConfig(
        Ship.WARBIRD,
        new ShipStat(210, 300, 40),                                      // rotation
        new ShipStat(thrustInitial, thrustMax, 2),                       // thrust
        new ShipStat(speedInitial, speedMax, 250),                       // speed
        new ShipStat(400, 1150, 166),                                    // recharge
        new ShipStat(energyInitial, energyMax, 100),                     // energy
        0.99,                                                            // linearDamping
        8.0,                                                             // turnResponsiveness
        1.0,                                                             // bounceRestitution
        250.0,                                                           // radarRange
        new BombStats(BombLevel.BOMB_1, bombMax, 10, 25L, 2000, 400),
        null,                                                            // gravBombs
        new BulletStats(BulletLevel.LEVEL_1, bulletMax, 10, 25L, 2000),
        new MineStats(BombLevel.BOMB_1, mineMax, 50, 500L, 0),
        new infinity.config.BurstStats(/* start */ 5, burstMax, /* speed */ 3000),
        new CountWithDelayStats(2, thorMax, 1000L),                      // thors
        new CountStats(10, repelMax),                                    // repels
        null,                                                            // decoys
        null,                                                            // bricks
        null,                                                            // rockets
        null,                                                            // portals
        null,                                                            // cloak
        null,                                                            // stealth
        null,                                                            // xradar
        null,                                                            // antiwarp
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
    final ShipSpawnSystem spawnSystem =
        systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed, registry, spawnSystem);
  }

  private static final class Fixture {
    private final GameSystemManager systems;
    private final DefaultEntityData ed;
    private final ConfigRegistrySystem registry;
    private final ShipSpawnSystem spawnSystem;

    private Fixture(
        final GameSystemManager systems,
        final DefaultEntityData ed,
        final ConfigRegistrySystem registry,
        final ShipSpawnSystem spawnSystem) {
      this.systems = systems;
      this.ed = ed;
      this.registry = registry;
      this.spawnSystem = spawnSystem;
    }

    private void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
