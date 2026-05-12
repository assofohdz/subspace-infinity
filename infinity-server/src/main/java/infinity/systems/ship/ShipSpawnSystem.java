// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.RadarShapeInfo;
import infinity.es.arena.ArenaId;
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
import infinity.settings.ConfigRegistrySystem;
import infinity.systems.BaseInfinitySystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Projects {@link ShipConfig} → ship stat components on spawn / arena-cross / ship-swap. Respawn resets live pools; tuning reload preserves them. See ADR 0001 + {@code config-pattern.md}. */
public class ShipSpawnSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(ShipSpawnSystem.class);

  // Subspace: MaximumRotation=400 = one full rotation/sec.
  private static final double ROTATION_UNITS_TO_RAD_SEC = (2.0 * Math.PI) / 400.0;

  // Subspace: MaximumRecharge is "energy recharge in 10 seconds" → divide by 10 for per-sec.
  private static final double RECHARGE_UNITS_TO_PER_SEC = 1.0 / 10.0;

  private EntityData ed;
  private ConfigRegistrySystem configRegistry;

  private EntitySet ships;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    // Ships in no-arena void (no ArenaId) intentionally not watched; reprojected on entry.
    ships = ed.getEntities(ShipType.class, ArenaId.class);
  }

  @Override
  protected void terminate() {
    ships.release();
    ships = null;
  }

  @Override
  public void update(final SimTime time) {
    ships.applyChanges();

    // Added — spawn-into-arena or re-entry from void: fresh ship, reset live pools.
    for (final Entity spawned : ships.getAddedEntities()) {
      applyConfigTo(spawned, true);
    }
    // Changed — usually an ArenaId rewrite (arena cross): preserve live pools.
    // ResetLivePool marker forces respawn semantics (ship-swap path); clear after one tick.
    for (final Entity changed : ships.getChangedEntities()) {
      final EntityId id = changed.getId();
      final boolean reset = ed.getComponent(id, ResetLivePool.class) != null;
      applyConfigTo(changed, reset);
      if (reset) {
        ed.removeComponent(id, ResetLivePool.class);
      }
    }
  }

  /** Re-projects stats on every in-arena ship (tuning reload, live pools preserved). Thread-safe. */
  public int reprojectAll() {
    int n = 0;
    final EntitySet allShips = ed.getEntities(ShipType.class, ArenaId.class);
    try {
      allShips.applyChanges();
      for (final Entity ship : allShips) {
        applyConfigTo(ship, false);
        n++;
      }
    } finally {
      allShips.release();
    }
    log.debug("reprojectAll: re-projected {} ship(s)", n);
    return n;
  }

  private void applyConfigTo(final Entity shipEntity, final boolean resetLivePool) {
    final ShipType shipType = shipEntity.get(ShipType.class);
    final ArenaId arena = shipEntity.get(ArenaId.class);
    if (shipType == null || shipType.getType() == null || arena == null) {
      return;
    }
    final ShipConfig cfg = configRegistry.forArena(arena).getShip(shipType.getType());
    if (cfg == null) {
      warnMissingShipConfig(shipType, arena);
      return;
    }
    project(shipEntity.getId(), cfg, resetLivePool);
    logProjectionApplied(shipEntity, shipType, arena, cfg, resetLivePool);
  }

  private static void warnMissingShipConfig(final ShipType shipType, final ArenaId arena) {
    if (log.isWarnEnabled()) {
      log.warn(
          "No ShipConfig for {} in arena {}; leaving defaults",
          shipType.getType(),
          arena.getArena());
    }
  }

  private void logProjectionApplied(
      final Entity shipEntity, final ShipType shipType, final ArenaId arena,
      final ShipConfig cfg, final boolean resetLivePool) {
    if (log.isInfoEnabled()) {
      log.info(
          "Projected ShipConfig {} ({}) from arena {} onto ship {}",
          shipType.getType(),
          resetLivePool ? "respawn" : "tuning",
          arena.getArena(),
          shipEntity.getId());
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "  stats: thrust={} speed={} rotation={} recharge={} energy={} linDamp={} turn={} bounce={}",
          cfg.thrust(),
          cfg.speed(),
          cfg.rotation(),
          cfg.recharge(),
          cfg.energy(),
          cfg.linearDamping(),
          cfg.turnResponsiveness(),
          cfg.bounceRestitution());
    }
  }

  private void project(final EntityId shipId, final ShipConfig cfg, final boolean resetLivePool) {
    projectThrust(shipId, cfg.thrust(), resetLivePool);
    projectSpeed(shipId, cfg.speed(), resetLivePool);
    projectRotation(shipId, cfg.rotation(), resetLivePool);
    projectEnergyStats(shipId, cfg.energy(), cfg.recharge(), resetLivePool);
    projectFeel(shipId, cfg);
    projectRadar(shipId, cfg);
    ShipWeaponsProjector.projectBombs(ed, shipId, cfg.bombs(), resetLivePool);
    ShipWeaponsProjector.projectBullets(ed, shipId, cfg.bullets(), resetLivePool);
    ShipWeaponsProjector.projectMines(ed, shipId, cfg.mines(), resetLivePool);
    ShipWeaponsProjector.projectBursts(ed, shipId, cfg.bursts(), resetLivePool);
    ShipWeaponsProjector.projectThors(ed, shipId, cfg.thors(), resetLivePool);
    ShipWeaponsProjector.projectRepels(ed, shipId, cfg.repels(), resetLivePool);
    ShipWeaponsProjector.projectDecoys(ed, shipId, cfg.decoys(), resetLivePool);
    ShipWeaponsProjector.projectBricks(ed, shipId, cfg.bricks(), resetLivePool);
    ShipWeaponsProjector.projectRockets(ed, shipId, cfg.rockets(), resetLivePool);
    ShipWeaponsProjector.projectPortals(ed, shipId, cfg.portals(), resetLivePool);
    ShipStatusProjector.projectCloak(ed, shipId, cfg.cloak(), resetLivePool);
    ShipStatusProjector.projectStealth(ed, shipId, cfg.stealth(), resetLivePool);
    ShipStatusProjector.projectXRadar(ed, shipId, cfg.xradar(), resetLivePool);
    ShipStatusProjector.projectAntiwarp(ed, shipId, cfg.antiwarp(), resetLivePool);
    projectRepellable(shipId, cfg.repellable());
  }

  private void projectRepellable(final EntityId shipId, final boolean repellable) {
    if (repellable) {
      ed.setComponent(shipId, new infinity.es.Repellable());
    } else {
      ed.removeComponent(shipId, infinity.es.Repellable.class);
    }
  }

  private void projectThrust(
      final EntityId shipId, final ShipStat stat, final boolean resetLivePool) {
    if (resetLivePool) {
      ed.setComponent(shipId, new Thrust(stat.initial()));
    }
    ed.setComponent(shipId, new ThrustStats(stat.max(), stat.upgrade()));
  }

  private void projectSpeed(
      final EntityId shipId, final ShipStat stat, final boolean resetLivePool) {
    if (resetLivePool) {
      ed.setComponent(shipId, new Speed(stat.initial()));
    }
    ed.setComponent(shipId, new SpeedStats(stat.max(), stat.upgrade()));
  }

  private void projectRotation(
      final EntityId shipId, final ShipStat stat, final boolean resetLivePool) {
    if (resetLivePool) {
      ed.setComponent(shipId, new Rotation(stat.initial() * ROTATION_UNITS_TO_RAD_SEC));
    }
    ed.setComponent(
        shipId,
        new RotationStats(
            stat.max() * ROTATION_UNITS_TO_RAD_SEC,
            stat.upgrade() * ROTATION_UNITS_TO_RAD_SEC));
  }

  /** Tuning reload preserves current effective cap (upgraded max); respawn re-projects from template. */
  private void projectEnergyStats(
      final EntityId shipId,
      final ShipStat energyStat,
      final ShipStat rechargeStat,
      final boolean resetLivePool) {
    if (resetLivePool) {
      ed.setComponent(shipId, new Energy(energyStat.initial()));
    }
    final int max;
    if (resetLivePool) {
      max = energyStat.initial();
    } else {
      // Tuning reload: preserve current effective cap so accumulated upgrades survive.
      final EnergyStats existing = ed.getComponent(shipId, EnergyStats.class);
      max = existing == null ? energyStat.initial() : existing.max();
    }
    ed.setComponent(
        shipId,
        new EnergyStats(
            max,
            energyStat.max(),
            energyStat.upgrade(),
            rechargeStat.initial() * RECHARGE_UNITS_TO_PER_SEC,
            rechargeStat.max() * RECHARGE_UNITS_TO_PER_SEC,
            rechargeStat.upgrade() * RECHARGE_UNITS_TO_PER_SEC));
  }

  private void projectFeel(final EntityId shipId, final ShipConfig cfg) {
    ed.setComponent(shipId, new LinearDamping(cfg.linearDamping()));
    ed.setComponent(shipId, new TurnResponsiveness(cfg.turnResponsiveness()));
    ed.setComponent(shipId, new BounceRestitution(cfg.bounceRestitution()));
  }

  private void projectRadar(final EntityId shipId, final ShipConfig cfg) {
    ed.setComponent(shipId, new RadarRange(cfg.radarRange()));
    // Blip name mirrors SISpatialFactory's "ship_<name>_blip" convention.
    ed.setComponent(shipId, RadarShapeInfo.create(cfg.type().getName() + "_blip", ed));
  }
}
