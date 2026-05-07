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
import infinity.es.ship.EnergyMax;
import infinity.es.ship.Health;
import infinity.es.ship.RadarRange;
import infinity.es.ship.Recharge;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.ShipType;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;
import infinity.es.ship.TurnResponsiveness;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstMax;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelMax;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorMaxCount;
import infinity.es.ship.weapons.BombCost;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombMaxLevel;
import infinity.es.ship.weapons.BulletCost;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletMaxLevel;
import infinity.es.ship.weapons.MineCost;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineMaxLevel;
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
            new BombStats(BombLevel.BOMB_1, BombLevel.BOMB_4, 10, 25L, /* speed */ 2000),
            new BulletStats(BulletLevel.LEVEL_1, BulletLevel.LEVEL_4, 10, 25L, /* speed */ 2000),
            new MineStats(BombLevel.BOMB_1, BombLevel.BOMB_4, 50, 500L),
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
            null);                                      // antiwarp (not authored)

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

      // Capability stat triples — direct passthrough.
      assertEquals(16, ed.getComponent(shipId, Thrust.class).getThrust());
      assertEquals(19, ed.getComponent(shipId, ThrustMax.class).getThrustMax());
      assertEquals(2010, ed.getComponent(shipId, Speed.class).getSpeed());
      assertEquals(3250, ed.getComponent(shipId, SpeedMax.class).getSpeedMax());

      // Rotation is in rad/sec (Subspace `400 = one rotation/sec` convention).
      assertEquals(
          210 * ROTATION_UNITS_TO_RAD_SEC,
          ed.getComponent(shipId, Rotation.class).getRadSec(),
          EPSILON);
      assertEquals(
          300 * ROTATION_UNITS_TO_RAD_SEC,
          ed.getComponent(shipId, RotationMax.class).getRadSecMax(),
          EPSILON);

      // Recharge is in energy/sec (Subspace `MaximumRecharge` is energy in 10s).
      assertEquals(
          400 * RECHARGE_UNITS_TO_PER_SEC,
          ed.getComponent(shipId, Recharge.class).getRechargePerSecond(),
          EPSILON);
      assertEquals(
          1150 * RECHARGE_UNITS_TO_PER_SEC,
          ed.getComponent(shipId, RechargeMax.class).getMaxRechargePerSecond(),
          EPSILON);

      // Energy live + max + Health (live pool reset on respawn).
      assertEquals(1000, ed.getComponent(shipId, Energy.class).getEnergy());
      assertEquals(1700, ed.getComponent(shipId, EnergyMax.class).getMaxEnergy());
      assertEquals(1000, ed.getComponent(shipId, Health.class).getHealth());

      // Feel knobs.
      assertEquals(0.99, ed.getComponent(shipId, LinearDamping.class).getDamping(), EPSILON);
      assertEquals(
          8.0, ed.getComponent(shipId, TurnResponsiveness.class).getRate(), EPSILON);
      assertEquals(
          1.0, ed.getComponent(shipId, BounceRestitution.class).getRestitution(), EPSILON);
      assertEquals(250.0, ed.getComponent(shipId, RadarRange.class).getRange(), EPSILON);

      // BombLevel: current level resets to start; max + cost project verbatim.
      assertEquals(BombLevel.BOMB_1, ed.getComponent(shipId, BombCurrentLevel.class).getLevel());
      assertEquals(BombLevel.BOMB_4, ed.getComponent(shipId, BombMaxLevel.class).getLevel());
      assertEquals(10, ed.getComponent(shipId, BombCost.class).getCost());

      // BulletLevel.
      assertEquals(BulletLevel.LEVEL_1, ed.getComponent(shipId, BulletCurrentLevel.class).getLevel());
      assertEquals(BulletLevel.LEVEL_4, ed.getComponent(shipId, BulletMaxLevel.class).getLevel());
      assertEquals(10, ed.getComponent(shipId, BulletCost.class).getCost());

      // Mines (reuse BombLevel enum).
      assertEquals(BombLevel.BOMB_1, ed.getComponent(shipId, MineCurrentLevel.class).getLevel());
      assertEquals(BombLevel.BOMB_4, ed.getComponent(shipId, MineMaxLevel.class).getLevel());
      assertEquals(50, ed.getComponent(shipId, MineCost.class).getCost());

      // Inventory counters: current resets to start, max projects verbatim.
      assertEquals(5, ed.getComponent(shipId, Burst.class).getCount());
      assertEquals(5, ed.getComponent(shipId, BurstMax.class).getCount());
      assertEquals(2, ed.getComponent(shipId, ThorCurrentCount.class).getCount());
      assertEquals(2, ed.getComponent(shipId, ThorMaxCount.class).getCount());
      assertEquals(10, ed.getComponent(shipId, Repel.class).getCount());
      assertEquals(20, ed.getComponent(shipId, RepelMax.class).getCount());

      // Slice 10 — projectile speeds project from BombStats.speed /
      // BulletStats.speed / BurstStats.speed onto BombSpeed / BulletSpeed /
      // BurstSpeed components. Stored raw (Subspace velocity units);
      // WeaponsSystem applies engine-tier scale + cap at fire time.
      assertEquals(
          2000,
          ed.getComponent(shipId, infinity.es.ship.weapons.BombSpeed.class).getSpeed());
      assertEquals(
          2000,
          ed.getComponent(shipId, infinity.es.ship.weapons.BulletSpeed.class).getSpeed());
      assertEquals(
          3000,
          ed.getComponent(shipId, infinity.es.ship.weapons.BurstSpeed.class).getSpeed());

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
}
