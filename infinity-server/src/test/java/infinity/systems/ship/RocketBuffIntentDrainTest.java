// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.Ship;
import infinity.config.BombStats;
import infinity.config.BurstStats;
import infinity.config.BulletStats;
import infinity.config.CountStats;
import infinity.config.CountWithDelayStats;
import infinity.config.MineStats;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Speed;
import infinity.es.ship.ShipType;
import infinity.es.ship.Thrust;
import infinity.es.ship.actions.RocketBuffIntent;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import org.junit.Test;

/**
 * Pins the {@link RocketBuffIntent} drain owned by
 * {@link ShipSpawnSystem} (BACKLOG C1 — closes the same-tick reproject +
 * buff-revert race). The intent is the only legitimate channel for
 * rocket-buff-driven {@link Thrust} / {@link Speed} writes after the
 * migration; {@code ConsumableSystem} emits activate intents and
 * {@code RocketBuffSystem} emits revert intents.
 *
 * <p>This test boots a minimal {@link GameSystemManager} with just
 * {@link EntityData}, {@link ConfigRegistrySystem}, and
 * {@link ShipSpawnSystem} so the drain can be exercised in isolation.
 * It does NOT register {@code ConsumableSystem} / {@code RocketBuffSystem}
 * — those systems' role is to emit intents; emitting an intent
 * directly is sufficient to exercise the drain.
 */
public class RocketBuffIntentDrainTest {

  @Test
  public void activateIntent_writesShipThrustAndSpeedNextDrain() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();

    try {
      final EntityId ship = ed.createEntity();
      // Seed Thrust/Speed at pre-buff values so the drain assertion is
      // unambiguous (the test would also pass with absent components,
      // but seeding documents intent).
      ed.setComponent(ship, new Thrust(16));
      ed.setComponent(ship, new Speed(2000));

      // Emit the activate intent — same shape ConsumableSystem.createRocketBuff
      // produces after the C1 migration.
      final EntityId intent = ed.createEntity();
      ed.setComponent(intent, new RocketBuffIntent(ship, 100, 3000));

      // One tick: ShipSpawnSystem.update drains the intent and writes
      // Thrust + Speed.
      systems.update();

      assertEquals(
          "Thrust written from activate intent", 100, ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Speed written from activate intent", 3000, ed.getComponent(ship, Speed.class).getSpeed());
      // Intent entity is consumed after drain (fire-and-forget shape).
      assertNull(
          "Intent entity removed after drain",
          ed.getComponent(intent, RocketBuffIntent.class));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  @Test
  public void revertIntent_writesShipThrustAndSpeedToSnapshotValues() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();

    try {
      final EntityId ship = ed.createEntity();
      // Ship is currently at rocket-active values (post-activate state).
      ed.setComponent(ship, new Thrust(100));
      ed.setComponent(ship, new Speed(3000));

      // Emit the revert intent — same shape RocketBuffSystem.onBuffRemoved
      // produces after the C1 migration (values from the cached pre-buff
      // ActiveBuff snapshot).
      final EntityId intent = ed.createEntity();
      ed.setComponent(intent, new RocketBuffIntent(ship, 16, 2000));

      systems.update();

      assertEquals(
          "Thrust reverted from revert intent",
          16,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Speed reverted from revert intent",
          2000,
          ed.getComponent(ship, Speed.class).getSpeed());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  /**
   * Race: same-tick activate + revert intents target the same ship.
   * Deterministic resolution per RaM rule #7 (stable ordering) — Zay-ES
   * iterates EntitySets in monotonically-increasing EntityId order, so
   * the later-created intent (higher EntityId) wins.
   *
   * <p>This scenario is rare in practice but plausible: an old buff
   * expires the same tick the player fires a new rocket. The buff
   * expiry's revert intent is emitted by {@code RocketBuffSystem.update}
   * which runs AFTER {@code ConsumableSystem.update} (see GameServer
   * registration order), so revert wins in that timing — the test
   * pins the entity-id-order rule generally.
   */
  @Test
  public void sameTickActivateAndRevert_lastByEntityIdWins() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();

    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Thrust(0));
      ed.setComponent(ship, new Speed(0));

      // Create activate FIRST (lower EntityId).
      final EntityId activate = ed.createEntity();
      ed.setComponent(activate, new RocketBuffIntent(ship, 100, 3000));

      // Then revert (higher EntityId) — wins per stable-ordering rule.
      final EntityId revert = ed.createEntity();
      ed.setComponent(revert, new RocketBuffIntent(ship, 16, 2000));

      systems.update();

      assertEquals(
          "Later-emitted intent (revert) wins per RaM rule #7",
          16,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Later-emitted intent (revert) wins per RaM rule #7",
          2000,
          ed.getComponent(ship, Speed.class).getSpeed());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  /**
   * Race: same-tick reproject (template projection in
   * {@code ShipSpawnSystem.applyConfigTo}) + revert intent. Pre-fix
   * this was the order-dependent C1 bug — direct revert writes from
   * {@code RocketBuffSystem.onBuffRemoved} could win or lose against a
   * reproject from arena cross / Groovy hot-reload depending on
   * tick-internal ordering. Post-fix: drain runs AFTER the
   * added/changed branches, so the intent deterministically wins.
   *
   * <p>This test seeds the ship-add branch (ship enters arena → fires
   * {@code applyConfigTo} with respawn projection) and emits a revert
   * intent in the same tick. Asserts the intent's values land on the
   * ship, NOT the config's base values.
   */
  @Test
  public void sameTickReprojectAndRevert_intentWinsOverTemplateProjection() {
    final ShipConfig warbird =
        new ShipConfig(
            Ship.WARBIRD,
            new ShipStat(210, 300, 40),     // rotation
            new ShipStat(/* base thrust = 16 */ 16, 19, 2),  // thrust
            new ShipStat(/* base speed = 2010 */ 2010, 3250, 250),  // speed
            new ShipStat(400, 1150, 166),   // recharge
            new ShipStat(1000, 1700, 100),  // energy
            0.99,                           // linearDamping
            8.0,                            // turnResponsiveness
            1.0,                            // bounceRestitution
            250.0,                          // radarRange
            new BombStats(
                infinity.BombLevel.BOMB_1, infinity.BombLevel.BOMB_4, 10, 25L, 2000, 400),
            new BulletStats(
                infinity.BulletLevel.LEVEL_1, infinity.BulletLevel.LEVEL_4, 10, 25L, 2000),
            new MineStats(
                infinity.BombLevel.BOMB_1, infinity.BombLevel.BOMB_4, 50, 500L, 0),
            new BurstStats(5, 5, 3000),
            new CountWithDelayStats(2, 2, 1000L),  // thors
            new CountStats(10, 20),                // repels
            null, null, null, null,
            null, null, null, null,
            true);

    final ConfigRegistry snapshot =
        ConfigRegistry.builder().ship(Ship.WARBIRD, warbird).build();

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

      // Stage 1: ship enters arena — this fires the respawn projection.
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      ed.setComponent(ship, arenaId);

      // Stage 2: same tick, emit a revert intent (e.g. an in-flight
      // rocket buff just expired while the ship was crossing arenas).
      // Intent carries pre-buff snapshot values (which are NOT the
      // config base values — that's the whole point of the race test).
      final int snapshotThrust = 42;
      final int snapshotSpeed = 2500;
      final EntityId intent = ed.createEntity();
      ed.setComponent(intent, new RocketBuffIntent(ship, snapshotThrust, snapshotSpeed));

      // One tick: ShipSpawnSystem.update runs the added-branch
      // (projects Thrust=16, Speed=2010 from config) THEN drains the
      // revert intent (overwrites to snapshotThrust=42, snapshotSpeed=2500).
      systems.update();

      assertEquals(
          "Revert intent wins over template projection (added branch)",
          snapshotThrust,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Revert intent wins over template projection (added branch)",
          snapshotSpeed,
          ed.getComponent(ship, Speed.class).getSpeed());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
