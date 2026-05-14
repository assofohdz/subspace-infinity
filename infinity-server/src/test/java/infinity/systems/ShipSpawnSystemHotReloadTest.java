// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.BulletLevel;
import infinity.Ship;
import infinity.config.BulletStats;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.ShipType;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedStats;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustStats;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.systems.ship.ShipSpawnSystem;
import org.junit.Test;

/**
 * Slice 3 of the spawn-projection test harness — pins the
 * "{@link ConfigRegistrySystem#replace} → {@link ShipSpawnSystem#reprojectAll}"
 * seam that the Groovy hot-reload (zone.groovy / arena.groovy / engine.groovy
 * watchers via {@code GroovyFileWatcher}) depends on for in-flight tuning
 * updates. The hot-reload landing commit ({@code 1f1be383}) was verified by
 * manual {@code sed}-and-watch-the-log; this file guards the regression
 * automatically.
 *
 * <p>Concretely: the production flow that reaches this seam is
 * {@code GroovyFileWatcher.poll()} → reloads zone/arena Groovy →
 * {@code ArenaSystem.applyZoneStartupConfig} →
 * {@code ConfigRegistrySystem.load(arenaId, …)} (which culminates in
 * {@link ConfigRegistrySystem#replace(ArenaId, ConfigRegistry)}) →
 * {@code ShipSpawnSystem.reprojectAll()}. The harness elides the file
 * watcher + Groovy loader (covered by their own unit tests) and asserts
 * the two ECS-side guarantees the seam must keep:
 *
 * <ol>
 *   <li><b>Capability stats track the new snapshot</b> — Thrust / Speed /
 *       Recharge / EnergyMax pick up the replaced template's values for
 *       every live ship in the arena.
 *   <li><b>Live pools survive the reload</b> — Health / Energy (current,
 *       not the cap) are preserved through reproject, so a damaged ship
 *       does not free-heal on Groovy edit. {@link ShipSpawnSystem}
 *       distinguishes "respawn projection" (gated on the
 *       {@code ResetLivePool} marker, fires on add) from "tuning
 *       projection" (no marker, fires on change / reprojectAll); the
 *       hot-reload path is unambiguously tuning.
 *   <li><b>Observer EntitySets see the change</b> — clients / reactors
 *       that watch the stat components observe the re-projected entity
 *       as "changed" after {@code applyChanges()}, which is what makes
 *       sync-to-network and HUD updates fire on hot-reload.
 *   <li><b>Per-arena isolation</b> — replacing arena X's snapshot does
 *       not corrupt ship state in arena Y. {@code reprojectAll()} walks
 *       every live ship, but each pulls its own arena's snapshot.
 * </ol>
 *
 * <p>No file watcher, no Groovy loader — those have their own tests
 * ({@code GroovyArenaLoaderTest}, {@code GroovyZoneLoaderTest},
 * {@code GroovySettingsHostTest}). The seam this file pins is what
 * those loaders fan into.
 */
public class ShipSpawnSystemHotReloadTest {

  private static final String TEST_ARENA_NAME = "test";
  private static final double EPSILON = 1e-9;
  // Mirrors ShipSpawnSystem's private constant so assertion math stays local.
  private static final double RECHARGE_UNITS_TO_PER_SEC = 1.0 / 10.0;

  // ──────────────────────────────────────────────────────────────────
  // Tests
  // ──────────────────────────────────────────────────────────────────

  /**
   * Base hot-reload round-trip: snapshot A's stats project on spawn,
   * snapshot B replaces A, {@code reprojectAll} pushes B's Stats
   * values onto every live ship while preserving the live Continuous
   * values (player retains accumulated cap upgrades through Groovy
   * reload, mirroring the Energy pilot's preserve-live-pool-on-tuning
   * semantics — see ADR 0001).
   */
  @Test
  public void replaceSnapshot_thenReprojectAll_pushesNewCapabilityStatsOntoLiveShips() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaId = new ArenaId(TEST_ARENA_NAME, EntityId.NULL_ID);
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 16, /* speed */ 2000)));

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, arenaId);

      // Tick 1: respawn projection (added entity) — live values seeded.
      f.systems.update();
      assertEquals(16, f.ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(2000, f.ed.getComponent(ship, Speed.class).getSpeed());

      // Hot-reload: replace snapshot, then reproject in place.
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 24, /* speed */ 2500)));
      final int reprojected = f.spawnSystem.reprojectAll();

      assertEquals("reprojectAll touched the only live ship", 1, reprojected);
      // Live Continuous values are PRESERVED on tuning reload (player
      // keeps whatever they had — same Energy-pilot pattern).
      assertEquals(
          "Live Thrust preserved on tuning reload",
          16,
          f.ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Live Speed preserved on tuning reload",
          2000,
          f.ed.getComponent(ship, Speed.class).getSpeed());
      // Stats records DO update — they carry the new hard cap +
      // upgrade values from snapshot B.
      assertEquals(
          "ThrustStats.max picks up snapshot B's cap after reproject",
          30,
          f.ed.getComponent(ship, ThrustStats.class).max());
      assertEquals(
          "SpeedStats.max picks up snapshot B's cap after reproject",
          5000,
          f.ed.getComponent(ship, SpeedStats.class).max());
    } finally {
      f.shutdown();
    }
  }

  /**
   * Reproject must preserve current Energy pool + the upgrade-tracked
   * {@link EnergyStats#max()} — a damaged ship must not free-heal on
   * Groovy edit, and a ship with accumulated cap upgrades must not lose
   * them. The {@link ShipSpawnSystem#reprojectAll} Javadoc promises
   * this; this test pins it.
   */
  @Test
  public void replaceSnapshot_thenReprojectAll_preservesLivePoolsOnDamagedShip() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaId = new ArenaId(TEST_ARENA_NAME, EntityId.NULL_ID);
      f.registry.replace(
          arenaId, snapshotWith(warbird(/* thrust */ 16, /* speed */ 2000)));

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, arenaId);
      f.systems.update();

      // Spawn projection seeded Energy pool = 1000 + EnergyStats.max
      // = 1000 (the initial in the ShipStat below). Simulate damage +
      // an accumulated cap upgrade.
      f.ed.setComponent(ship, new Energy(250));
      // Imagine a player picked up cap-bump prizes: stats.max was
      // bumped from 1000 to 1200 by EnergyStatsSystem. Reflect that.
      final EnergyStats existing = f.ed.getComponent(ship, EnergyStats.class);
      f.ed.setComponent(ship,
          new EnergyStats(
              1200,
              existing.hardMax(),
              existing.upgrade(),
              existing.rechargePerSecond(),
              existing.rechargeMax(),
              existing.rechargeUpgrade()));

      // Hot-reload to a snapshot with a *larger* hard cap so any
      // accidental respawn-projection would visibly overwrite
      // EnergyStats.max=1200 with the new initial=1100. Tuning
      // projection must leave .max alone but pick up the new
      // hardMax/upgrade.
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 24, /* speed */ 2500)));
      f.spawnSystem.reprojectAll();

      assertEquals(
          "Energy pool preserved across reproject (tuning, not respawn)",
          250,
          f.ed.getComponent(ship, Energy.class).getEnergy());
      final EnergyStats reprojected = f.ed.getComponent(ship, EnergyStats.class);
      assertEquals(
          "EnergyStats.max preserved across reproject (player keeps cap upgrades)",
          1200,
          reprojected.max());
      assertEquals(
          "EnergyStats.hardMax picks up snapshot B's value",
          2400,
          reprojected.hardMax());
    } finally {
      f.shutdown();
    }
  }

  /**
   * Diff-event surface: an external observer {@link EntitySet} watching
   * the projected capability components sees the re-projected ship in
   * {@code getChangedEntities()} after {@code reprojectAll} + the next
   * {@code applyChanges()}. This is what makes SimEthereal sync, the
   * client-side HUD reactor, and any other downstream observer notice
   * the hot-reload.
   */
  @Test
  public void reprojectAll_surfacesChangedEntityToObserverEntitySet() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaId = new ArenaId(TEST_ARENA_NAME, EntityId.NULL_ID);
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 16, /* speed */ 2000)));

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, arenaId);
      f.systems.update();

      // Stand up an observer EntitySet *after* the spawn so the initial
      // projection lands as an "added" event. Drain that, leaving the set
      // in a stable post-spawn state; the next applyChanges() should
      // surface anything reprojectAll() writes as a *change*.
      //
      // Watch ThrustStats (not Thrust) — under the ADR-0001 model the
      // tuning reproject preserves live Thrust and re-writes the Stats
      // bundle. The Stats record carries the player-observable hard cap.
      final EntitySet observer = f.ed.getEntities(ThrustStats.class, ArenaId.class);
      try {
        observer.applyChanges();
        assertEquals("observer sees the live ship after spawn", 1, observer.size());
        // Sanity: post-applyChanges, no pending change events.
        assertTrue(
            "no changed events before hot-reload",
            observer.getChangedEntities().isEmpty());

        // Hot-reload.
        f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 24, /* speed */ 2500)));
        f.spawnSystem.reprojectAll();
        observer.applyChanges();

        // The reproject must surface as a changed entity, not an
        // add/remove — the ship's identity is stable.
        final java.util.Set<Entity> changed = observer.getChangedEntities();
        assertEquals(
            "reproject surfaces exactly one changed entity to observers",
            1,
            changed.size());
        assertEquals(
            "the changed entity is the live ship",
            ship,
            changed.iterator().next().getId());
        // And the observed component snapshot reflects the new value
        // (would fail if reproject wrote into a stale view).
        assertEquals(
            30, changed.iterator().next().get(ThrustStats.class).max());
      } finally {
        observer.release();
      }
    } finally {
      f.shutdown();
    }
  }

  /**
   * Per-arena isolation: replacing arena X's snapshot does not corrupt
   * ship state in arena Y. {@code reprojectAll()} walks every live ship,
   * but each pulls its own arena's snapshot via
   * {@link ConfigRegistrySystem#forArena}, so a per-arena hot-reload
   * stays per-arena.
   */
  @Test
  public void replaceSnapshot_inOneArena_doesNotBleedIntoOtherArena() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaX = new ArenaId("arenaX", EntityId.NULL_ID);
      final ArenaId arenaY = new ArenaId("arenaY", EntityId.NULL_ID);

      // Two arenas, two distinct snapshots authored at startup.
      f.registry.replace(arenaX, snapshotWith(warbird(/* thrust */ 16, /* speed */ 2000)));
      f.registry.replace(arenaY, snapshotWith(warbird(/* thrust */ 12, /* speed */ 1800)));

      final EntityId shipX = f.ed.createEntity();
      f.ed.setComponent(shipX, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(shipX, arenaX);
      final EntityId shipY = f.ed.createEntity();
      f.ed.setComponent(shipY, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(shipY, arenaY);
      f.systems.update();

      // Sanity — each ship landed its own arena's snapshot.
      assertEquals(16, f.ed.getComponent(shipX, Thrust.class).getThrust());
      assertEquals(12, f.ed.getComponent(shipY, Thrust.class).getThrust());

      // Hot-reload arena X only — arena Y's snapshot is untouched.
      f.registry.replace(arenaX, snapshotWith(warbird(/* thrust */ 24, /* speed */ 2500)));
      f.spawnSystem.reprojectAll();

      // Live Thrust is preserved on tuning reload (Energy-pilot
      // pattern). Check ThrustStats.max for the per-arena hot-reload
      // observability — that IS rewritten on tuning reload.
      assertEquals(
          "shipX picks up arena X's new Stats.max",
          30,
          f.ed.getComponent(shipX, ThrustStats.class).max());
      assertEquals(
          "shipY's Stats.max matches arena Y's unchanged snapshot — no cross-arena bleed",
          18,
          f.ed.getComponent(shipY, ThrustStats.class).max());
      // Live Thrust preserved for both ships through their tuning reload.
      assertEquals(
          "shipX live Thrust preserved",
          16,
          f.ed.getComponent(shipX, Thrust.class).getThrust());
      assertEquals(
          "shipY live Thrust preserved",
          12,
          f.ed.getComponent(shipY, Thrust.class).getThrust());
    } finally {
      f.shutdown();
    }
  }

  /**
   * Derived-unit conversions (Recharge in units/sec) still apply on
   * reproject — would catch a regression where reproject called a raw
   * setComponent path that bypassed the unit conversion the respawn
   * projection performs.
   */
  @Test
  public void reprojectAll_appliesUnitConversionsForRechargeStats() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaId = new ArenaId(TEST_ARENA_NAME, EntityId.NULL_ID);
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 16, /* speed */ 2000)));

      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, arenaId);
      f.systems.update();

      // Hot-reload changes recharge from (400, 1150, 166) to (600, 1500, 200).
      // The new snapshot is authored via warbird(thrust=24, speed=2500); see
      // builder below for the recharge values it pins.
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 24, /* speed */ 2500)));
      f.spawnSystem.reprojectAll();

      final EnergyStats stats = f.ed.getComponent(ship, EnergyStats.class);
      assertEquals(
          "EnergyStats.rechargePerSecond picks up new initial * unit conversion",
          600 * RECHARGE_UNITS_TO_PER_SEC,
          stats.rechargePerSecond(),
          EPSILON);
      assertEquals(
          "EnergyStats.rechargeMax picks up new max * unit conversion",
          1500 * RECHARGE_UNITS_TO_PER_SEC,
          stats.rechargeMax(),
          EPSILON);
    } finally {
      f.shutdown();
    }
  }

  /**
   * No live ships, reproject is a no-op. Returns 0 — pins that the
   * hot-reload path on an empty arena doesn't blow up.
   */
  @Test
  public void reprojectAll_withNoLiveShips_isNoOp() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaId = new ArenaId(TEST_ARENA_NAME, EntityId.NULL_ID);
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 16, /* speed */ 2000)));

      final int reprojected = f.spawnSystem.reprojectAll();
      assertEquals("no live ships → reprojectAll returns 0", 0, reprojected);
    } finally {
      f.shutdown();
    }
  }

  /**
   * After a hot-reload, the {@link ShipSpawnSystem#reprojectAll} contract
   * is "every ship currently in scope" — so spawning a *new* ship after
   * the reload also picks up the new config, via the normal added-entity
   * pathway. Pins that the reload path doesn't put the system in some
   * weird state where subsequent spawns regress to the old snapshot.
   */
  @Test
  public void afterReproject_nextSpawnPicksUpNewSnapshot() {
    final Fixture f = newFixture();
    try {
      final ArenaId arenaId = new ArenaId(TEST_ARENA_NAME, EntityId.NULL_ID);
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 16, /* speed */ 2000)));

      final EntityId firstShip = f.ed.createEntity();
      f.ed.setComponent(firstShip, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(firstShip, arenaId);
      f.systems.update();

      // Hot-reload + reproject existing fleet.
      f.registry.replace(arenaId, snapshotWith(warbird(/* thrust */ 24, /* speed */ 2500)));
      f.spawnSystem.reprojectAll();

      // Now a new ship joins. The spawn path (ships.getAddedEntities)
      // should also see the new snapshot, not snapshot A.
      final EntityId latecomer = f.ed.createEntity();
      f.ed.setComponent(latecomer, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(latecomer, arenaId);
      f.systems.update();

      assertEquals(
          "Latecomer spawns with snapshot B's Thrust initial (added-branch resets live value)",
          24,
          f.ed.getComponent(latecomer, Thrust.class).getThrust());
      // The original ship's live Thrust was preserved through tuning
      // reload (Energy-pilot pattern). Stats.max picked up snapshot B's
      // ceiling.
      assertEquals(
          "Original ship's live Thrust preserved through tuning reload",
          16,
          f.ed.getComponent(firstShip, Thrust.class).getThrust());
      assertEquals(
          "Original ship's ThrustStats.max picked up snapshot B's cap",
          30,
          f.ed.getComponent(firstShip, ThrustStats.class).max());
      // Live-pool semantics still hold for the latecomer (it's a fresh
      // spawn — added event → respawn projection → Energy seeded).
      assertNotNull(
          "Latecomer received an Energy pool via respawn projection",
          f.ed.getComponent(latecomer, Energy.class));
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────
  // Fixtures
  // ──────────────────────────────────────────────────────────────────

  /**
   * Standard {@code (thrust, speed)}-parameterized WARBIRD template used
   * across the slice 3 tests. The "snapshot A" baseline pairs
   * {@code thrust=16, speed=2000} (the slice 1 numbers); the "snapshot B"
   * baseline pairs {@code thrust=24, speed=2500}. Recharge / energy / cap
   * values are tied to the thrust value so the two snapshots differ on
   * every capability stat we assert — keeping the assertion math
   * dimensionally honest if a future projection rewires the units.
   */
  private static ShipConfig warbird(final int thrust, final int speed) {
    // Snapshot A: thrust=16 → (400, 1150) recharge, (1000, 1700) energy.
    // Snapshot B: thrust=24 → (600, 1500) recharge, (1100, 2400) energy.
    final int rechargeInitial = (thrust == 16) ? 400 : 600;
    final int rechargeMax = (thrust == 16) ? 1150 : 1500;
    final int energyInitial = (thrust == 16) ? 1000 : 1100;
    final int energyMax = (thrust == 16) ? 1700 : 2400;
    return new ShipConfig(
        Ship.WARBIRD,
        new ShipStat(210, 300, 40),                  // rotation
        new ShipStat(thrust, thrust + 6, 2),         // thrust (16, 22) vs (24, 30)
        new ShipStat(speed, speed * 2, 250),         // speed (2000, 4000) vs (2500, 5000)
        new ShipStat(rechargeInitial, rechargeMax, 166), // recharge
        new ShipStat(energyInitial, energyMax, 100),     // energy
        0.99,                                        // linearDamping
        8.0,                                         // turnResponsiveness
        1.0,                                         // bounceRestitution
        250.0,                                       // radarRange
        null,                                        // bombs
        null,                                        // gravBombs
        new BulletStats(BulletLevel.LEVEL_1, BulletLevel.LEVEL_4, 10, 25L, /* speed */ 2000),
        null,                                        // mines
        null,                                        // bursts
        null,                                        // thors
        null,                                        // repels
        null,                                        // decoys
        null,                                        // bricks
        null,                                        // rockets
        null,                                        // portals
        null,                                        // cloak
        null,                                        // stealth
        null,                                        // xradar
        null,                                        // antiwarp
        true);                                       // repellable
  }

  private static ConfigRegistry snapshotWith(final ShipConfig warbird) {
    return ConfigRegistry.builder().ship(Ship.WARBIRD, warbird).build();
  }

  /**
   * Stand up the same minimal harness used in {@code ShipSpawnSystemTest}:
   * {@link GameSystemManager} wiring {@link EntityData},
   * {@link ConfigRegistrySystem}, and {@link ShipSpawnSystem} via the
   * production {@code register(Class, instance)} idiom so
   * {@code requireSystem} lookups resolve at initialize time.
   */
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
