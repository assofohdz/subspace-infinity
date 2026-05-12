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
import infinity.config.BurstStats;
import infinity.config.CountStats;
import infinity.config.CountWithDelayStats;
import infinity.config.BulletStats;
import infinity.config.MineStats;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.arena.ArenaId;
import infinity.systems.ship.ShipSpawnSystem;
import infinity.es.ship.BounceRestitution;
import infinity.es.ship.LinearDamping;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.RadarRange;
import infinity.es.ship.ResetLivePool;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationStats;
import infinity.es.ship.ShipType;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedStats;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustStats;
import infinity.es.ship.TurnResponsiveness;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelMax;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorMaxCount;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import org.junit.Test;

/**
 * First slice of the spawn-projection test harness — boots a minimal
 * {@link GameSystemManager} with only {@link EntityData},
 * {@link ConfigRegistrySystem}, and {@link ShipSpawnSystem}, then asserts that
 * adding a ship entity (with {@link ShipType} + {@link ArenaId}) projects a
 * fully-specified {@link ShipConfig} onto the documented per-entity components.
 *
 * <p>Establishes the harness pattern other Pattern 4 / spawn / projection
 * refactors will copy. See
 * {@code .scratch/spawn-projection-test-harness/PRD.md}.
 */
public class ShipSpawnSystemTest {

  private static final double EPSILON = 1e-9;
  // Subspace conventions copied from ShipSpawnSystem so the assertions remain
  // local — they break loudly if either the source-of-truth constant or the
  // projection arithmetic drifts.
  private static final double ROTATION_UNITS_TO_RAD_SEC = (2.0 * Math.PI) / 400.0;
  private static final double RECHARGE_UNITS_TO_PER_SEC = 1.0 / 10.0;

  @Test
  public void respawn_projectsAllConfigFieldsOntoFreshShipEntity() {
    final ShipConfig warbird =
        new ShipConfig(
            Ship.WARBIRD,
            new ShipStat(210, 300, 40),     // rotation
            new ShipStat(16, 19, 2),        // thrust
            new ShipStat(2010, 3250, 250),  // speed
            new ShipStat(400, 1150, 166),   // recharge
            new ShipStat(1000, 1700, 100),  // energy
            0.99,                           // linearDamping
            8.0,                            // turnResponsiveness
            1.0,                            // bounceRestitution
            250.0,                          // radarRange
            new BombStats(BombLevel.BOMB_1, BombLevel.BOMB_4, 10, 25L, /* speed */ 2000, /* thrust */ 400),
            new BulletStats(BulletLevel.LEVEL_1, BulletLevel.LEVEL_4, 10, 25L, /* speed */ 2000),
            new MineStats(BombLevel.BOMB_1, BombLevel.BOMB_4, 50, 500L, /* speed */ 0),
            new BurstStats(/* start */ 5, /* max */ 5, /* speed */ 3000),
            new CountWithDelayStats(2, 2, 1000L),       // thors
            new CountStats(10, 20),                     // repels
            null,                                       // decoys (disallow)
            null,                                       // bricks (disallow)
            null,                                       // rockets (disallow)
            null,                                       // portals (disallow)
            null,                                       // cloak (not authored)
            null,                                       // stealth (not authored)
            null,                                       // xradar (not authored)
            null,                                       // antiwarp (not authored)
            true);                                      // repellable

    final ConfigRegistry snapshot =
        ConfigRegistry.builder().ship(Ship.WARBIRD, warbird).build();

    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    // GameServer wires both systems via `register(Class, instance)` not
    // `addSystem(...)` — the former adds to the lookup index AND the system
    // list, so `AbstractGameSystem.getSystem(Class)` resolves them. With
    // addSystem alone, ShipSpawnSystem's `getSystem(ConfigRegistrySystem.class)`
    // returns null and projection silently no-ops.
    final ConfigRegistrySystem registry =
        systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();

    try {
      final ArenaId arenaId = new ArenaId("test", EntityId.NULL_ID);
      registry.replace(arenaId, snapshot);

      final EntityId shipId = ed.createEntity();
      ed.setComponent(shipId, new ShipType(Ship.WARBIRD));
      ed.setComponent(shipId, arenaId);

      // First tick: ShipSpawnSystem.update() drains getAddedEntities() and
      // projects the config in respawn mode (live pools reset).
      systems.update();

      // Capability stat split (post-ADR-0001): live Continuous value +
      // bundled Stats record carrying max + upgrade.
      assertEquals(16, ed.getComponent(shipId, Thrust.class).getThrust());
      final ThrustStats thrustStats = ed.getComponent(shipId, ThrustStats.class);
      assertEquals(19, thrustStats.max());
      assertEquals(2, thrustStats.upgrade());
      assertEquals(2010, ed.getComponent(shipId, Speed.class).getSpeed());
      final SpeedStats speedStats = ed.getComponent(shipId, SpeedStats.class);
      assertEquals(3250, speedStats.max());
      assertEquals(250, speedStats.upgrade());

      // Rotation is in rad/sec (Subspace `400 = one rotation/sec` convention).
      assertEquals(
          210 * ROTATION_UNITS_TO_RAD_SEC,
          ed.getComponent(shipId, Rotation.class).getRadSec(),
          EPSILON);
      final RotationStats rotationStats = ed.getComponent(shipId, RotationStats.class);
      assertEquals(300 * ROTATION_UNITS_TO_RAD_SEC, rotationStats.max(), EPSILON);
      assertEquals(40 * ROTATION_UNITS_TO_RAD_SEC, rotationStats.upgrade(), EPSILON);

      // Energy live pool (was Health pre-ADR) + EnergyStats bundle.
      // EnergyStats.max + hardMax: replaces Energy + EnergyMax.
      // EnergyStats.rechargePerSecond + rechargeMax: replaces Recharge
      // + RechargeMax (in energy/sec — Subspace MaximumRecharge is
      // energy in 10s).
      assertEquals(1000, ed.getComponent(shipId, Energy.class).getEnergy());
      final EnergyStats stats = ed.getComponent(shipId, EnergyStats.class);
      assertEquals(1000, stats.max());
      assertEquals(1700, stats.hardMax());
      assertEquals(100, stats.upgrade());
      assertEquals(
          400 * RECHARGE_UNITS_TO_PER_SEC, stats.rechargePerSecond(), EPSILON);
      assertEquals(
          1150 * RECHARGE_UNITS_TO_PER_SEC, stats.rechargeMax(), EPSILON);
      assertEquals(
          166 * RECHARGE_UNITS_TO_PER_SEC, stats.rechargeUpgrade(), EPSILON);

      // Feel knobs.
      assertEquals(0.99, ed.getComponent(shipId, LinearDamping.class).getDamping(), EPSILON);
      assertEquals(
          8.0, ed.getComponent(shipId, TurnResponsiveness.class).getRate(), EPSILON);
      assertEquals(
          1.0, ed.getComponent(shipId, BounceRestitution.class).getRestitution(), EPSILON);
      assertEquals(250.0, ed.getComponent(shipId, RadarRange.class).getRange(), EPSILON);

      // BombLevel: current level resets to start; cap + cost live on the bundled BombStats record.
      assertEquals(BombLevel.BOMB_1, ed.getComponent(shipId, BombCurrentLevel.class).getLevel());
      final infinity.es.ship.weapons.BombStats bombStats =
          ed.getComponent(shipId, infinity.es.ship.weapons.BombStats.class);
      assertNotNull("BombStats must be projected on respawn", bombStats);
      assertEquals(BombLevel.BOMB_4, bombStats.max());
      assertEquals(10, bombStats.fireCostEnergy());

      // BulletLevel.
      assertEquals(BulletLevel.LEVEL_1, ed.getComponent(shipId, BulletCurrentLevel.class).getLevel());
      final infinity.es.ship.weapons.BulletStats bulletStats =
          ed.getComponent(shipId, infinity.es.ship.weapons.BulletStats.class);
      assertNotNull("BulletStats must be projected on respawn", bulletStats);
      assertEquals(BulletLevel.LEVEL_4, bulletStats.max());
      assertEquals(10, bulletStats.fireCostEnergy());

      // Mines (reuse BombLevel enum).
      assertEquals(BombLevel.BOMB_1, ed.getComponent(shipId, MineCurrentLevel.class).getLevel());
      final infinity.es.ship.weapons.MineStats mineStats =
          ed.getComponent(shipId, infinity.es.ship.weapons.MineStats.class);
      assertNotNull("MineStats must be projected on respawn", mineStats);
      assertEquals(BombLevel.BOMB_4, mineStats.max());
      assertEquals(50, mineStats.dropCostEnergy());

      // Inventory counters: current resets to start. BurstStats bundles max + speed.
      assertEquals(5, ed.getComponent(shipId, Burst.class).getCount());
      final infinity.es.ship.actions.BurstStats burstStats =
          ed.getComponent(shipId, infinity.es.ship.actions.BurstStats.class);
      assertNotNull("BurstStats must be projected on respawn", burstStats);
      assertEquals(5, burstStats.max());
      assertEquals(2, ed.getComponent(shipId, ThorCurrentCount.class).getCount());
      assertEquals(2, ed.getComponent(shipId, ThorMaxCount.class).getCount());
      assertEquals(10, ed.getComponent(shipId, Repel.class).getCount());
      assertEquals(20, ed.getComponent(shipId, RepelMax.class).getCount());

      // Slice 10 — projectile speeds bundled into the per-aspect *Stats record.
      // Stored raw (Subspace velocity units); WeaponsFireSystem applies
      // engine-tier scale + cap at fire time.
      assertEquals(2000, bombStats.speed());
      assertEquals(2000, bulletStats.speed());
      assertEquals(3000, burstStats.speed());

      // Slice S2 — BombStats.thrust drives recoil at fire time.
      assertEquals(400, bombStats.thrust());

      // Weapon fire-delay components carry runtime state (start/delta nanos),
      // so existence is the right assertion here. Slice 2 covers the
      // tuning-projection contract that re-creates them with a fresh start.
      assertNotNull(
          "BombFireDelay must be projected on respawn",
          ed.getComponent(shipId, infinity.es.ship.weapons.BombFireDelay.class));
      assertNotNull(
          "BulletFireDelay must be projected on respawn",
          ed.getComponent(shipId, infinity.es.ship.weapons.BulletFireDelay.class));
      assertNotNull(
          "MineFireDelay must be projected on respawn",
          ed.getComponent(shipId, infinity.es.ship.weapons.MineFireDelay.class));
      assertNotNull(
          "ThorFireDelay must be projected on respawn",
          ed.getComponent(shipId, infinity.es.ship.actions.ThorFireDelay.class));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  /**
   * Ship-swap from a no-bombs ship to a bombs-equipped ship via
   * {@code AvatarSystem.requestShipChange}'s remove+set on {@link ShipType}
   * coalesces in Zay-ES into a single "changed" event (not added/removed).
   * Without the {@link ResetLivePool} marker, that surfaces as
   * {@code resetLivePool=false} in {@code ShipSpawnSystem.update()} and the
   * gated {@code BombCurrentLevel} (et al.) never get written for the new
   * ship — the bomb {@code EntitySet} membership filter then excludes the
   * ship and {@code canAttackBomb} returns false, even though the new
   * config has a {@code bombs} block.
   *
   * <p>This test pins the fix: stamping {@link ResetLivePool} alongside the
   * {@code ShipType} swap forces the change branch to project with
   * {@code resetLivePool=true}, and the marker is cleared afterward so a
   * subsequent in-place tuning event doesn't redundantly reset the live
   * pools.
   */
  @Test
  public void shipSwap_resetLivePoolMarker_projectsBombCurrentLevelOnTypeChange() {
    // WARBIRD: no bombs (matches trench's typed config)
    final ShipConfig warbird =
        new ShipConfig(
            Ship.WARBIRD,
            new ShipStat(200, 300, 20),
            new ShipStat(16, 24, 2),
            new ShipStat(2000, 6000, 200),
            new ShipStat(4000, 8000, 200),
            new ShipStat(1500, 3000, 100),
            0.99,
            2.0,
            0.3,
            50.0,
            null,                                       // bombs (no bombs)
            new BulletStats(BulletLevel.LEVEL_3, BulletLevel.LEVEL_3, 450, 100L, 5000),
            null,                                       // mines
            null, null, null, null, null, null, null, null, null, null, null,
            true);                                      // repellable

    // JAVELIN: bombs equipped (matches trench's typed config)
    final ShipConfig javelin =
        new ShipConfig(
            Ship.JAVELIN,
            new ShipStat(200, 200, 0),
            new ShipStat(13, 24, 0),
            new ShipStat(1900, 6000, 0),
            new ShipStat(1500, 1500, 0),
            new ShipStat(1500, 1500, 0),
            0.99,
            2.0,
            0.3,
            50.0,
            new BombStats(BombLevel.BOMB_1, BombLevel.BOMB_1, 1100, 75L, 2250, 400),
            new BulletStats(BulletLevel.LEVEL_1, BulletLevel.LEVEL_1, 300, 60L, -900),
            null,                                       // mines
            null, null, null, null, null, null, null, null, null, null, null,
            true);                                      // repellable

    final ConfigRegistry snapshot =
        ConfigRegistry.builder()
            .ship(Ship.WARBIRD, warbird)
            .ship(Ship.JAVELIN, javelin)
            .build();

    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    final ConfigRegistrySystem registry =
        systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();

    try {
      final ArenaId arenaId = new ArenaId("test", EntityId.NULL_ID);
      registry.replace(arenaId, snapshot);

      // Initial spawn as WARBIRD — surfaces in getAddedEntities,
      // resetLivePool=true. Warbird has no bombs config → projectBombs
      // early-returns → no BombCurrentLevel written.
      final EntityId shipId = ed.createEntity();
      ed.setComponent(shipId, new ShipType(Ship.WARBIRD));
      ed.setComponent(shipId, arenaId);
      systems.update();
      org.junit.Assert.assertNull(
          "WARBIRD has no bombs config; BombCurrentLevel not projected",
          ed.getComponent(shipId, BombCurrentLevel.class));

      // Ship-swap to JAVELIN, mimicking AvatarSystem.requestShipChange:
      // remove+set on ShipType (Zay-ES coalesces to changed, not
      // added/removed) AND stamp ResetLivePool to flag respawn semantics.
      ed.removeComponent(shipId, ShipType.class);
      ed.setComponent(shipId, new ShipType(Ship.JAVELIN));
      ed.setComponent(shipId, new ResetLivePool());
      systems.update();

      // Marker cleared by ShipSpawnSystem after one tick.
      org.junit.Assert.assertNull(
          "ResetLivePool marker cleared after respawn projection",
          ed.getComponent(shipId, ResetLivePool.class));

      // BombCurrentLevel now set to JAVELIN's start (BOMB_1). The bomb
      // EntitySet filter `(BombCurrentLevel, BombFireDelay, BombCost)`
      // now matches → canAttackBomb passes its first gate.
      assertNotNull(
          "BombCurrentLevel must be projected after ship-swap respawn",
          ed.getComponent(shipId, BombCurrentLevel.class));
      assertEquals(
          BombLevel.BOMB_1,
          ed.getComponent(shipId, BombCurrentLevel.class).getLevel());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
