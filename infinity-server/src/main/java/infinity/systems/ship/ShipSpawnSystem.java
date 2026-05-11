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

/**
 * Projects {@link ShipConfig} templates from {@link ConfigRegistrySystem}
 * onto ship entities at spawn time, writing the matching stat components
 * (current / max / upgrade triples for thrust, speed, rotation, recharge,
 * energy).
 *
 * <p>The trigger is membership in the watched {@code (ShipType, ArenaId)}
 * set. Three flows feed it:
 * <ul>
 *   <li><b>Spawn into an arena</b> — {@code GameSessionHostedService} /
 *       {@code BasicEnvironment} attach {@code ArenaId} alongside
 *       {@code ShipType} when the spawn coord lands inside a loaded arena;
 *       the entity appears in the set ({@code getAddedEntities}) and its
 *       config is projected.
 *   <li><b>Arena cross</b> — {@link infinity.systems.ArenaMembershipSystem}
 *       rewrites {@code ArenaId} when a ship enters a different arena
 *       sensor; the entity surfaces as a change ({@code getChangedEntities})
 *       or a remove + add, and the new arena's config replaces the old.
 *   <li><b>Ship swap</b> — the player presses 1-8; {@code AvatarSystem}
 *       remove + sets {@code ShipType}, surfacing as an add event so the
 *       new ship type's config is projected.
 * </ul>
 *
 * <p>Ships in no-arena void (no {@code ArenaId}) are not in the set and
 * receive no projection — they retain whatever stats they last had until
 * they cross into an arena.
 *
 * <p><b>Two projection modes</b> — distinguished by whether the depleting
 * resource pools ({@link Energy} pool + Continuous Thrust/Speed/Rotation)
 * are reset to {@code stat.initial()}:
 * <ul>
 *   <li><b>Respawn projection</b> (everything resets) — fires on
 *       {@code getAddedEntities}: spawn-into-arena, re-entry from void, and
 *       ship-swap (AvatarSystem remove+set on ShipType surfaces here). The
 *       ship is conceptually "fresh", so a full reset is correct.
 *   <li><b>Tuning projection</b> (everything except live pools resets) —
 *       fires on {@code getChangedEntities} (arena cross while alive) and on
 *       {@link #reprojectAll} (Groovy hot-reload). Stats records pick up the
 *       new template values; the live {@link Energy} pool and the live
 *       Thrust/Speed/Rotation values are preserved so a damaged or
 *       cap-upgraded ship doesn't lose state on tuning reload.
 * </ul>
 *
 * <p><b>ADR 0001 Continuous + Stats split.</b> Post-Energy-pilot the ship
 * stats follow the canonical quadruple: {@code <Aspect>} (live, Continuous)
 * + {@code <Aspect>Stats} (max/upgrade bundle) + {@code <Aspect>Change}
 * (Continuous delta) + {@code <Aspect>StatsChange} (Stats delta). Runtime
 * mutation flows through Change holder entities drained by the per-aspect
 * canonical writer ({@code EnergySystem}, {@code RotationSystem},
 * {@code SpeedSystem}, {@code ThrustSystem}); this spawn system writes
 * Continuous + Stats at projection time only.
 */
public class ShipSpawnSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(ShipSpawnSystem.class);

  /**
   * Subspace convention: {@code MaximumRotation=400} means one full rotation
   * per second. Convert the raw INI/Groovy integer to radians per second.
   * Verified against SubspaceServer's {@code ClientSettingsConfig.cs} line 388
   * ("400 = full rotation in 1 second").
   */
  private static final double ROTATION_UNITS_TO_RAD_SEC = (2.0 * Math.PI) / 400.0;

  /**
   * Subspace convention: {@code MaximumRecharge} is "amount of energy recharge
   * in 10 seconds" (per SubspaceServer {@code TeamVersusStats.cs:4398}), or
   * equivalently {@code energy/tick = MaximumRecharge / 1000} at the classic
   * 100 Hz tick. Convert to energy/second by dividing by 10.
   */
  private static final double RECHARGE_UNITS_TO_PER_SEC = 1.0 / 10.0;

  private EntityData ed;
  private ConfigRegistrySystem configRegistry;

  private EntitySet ships;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);

    // Watch ships that are currently in some arena. Ships without ArenaId
    // (no-arena void) are intentionally not in the set; they get reprojected
    // as soon as ArenaMembershipSystem assigns an ArenaId on entry.
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

    // Added: ship just gained both ShipType and ArenaId — spawn-into-arena
    // or re-entry from no-arena void. (Ship-swap surfaces in the changed
    // branch below: AvatarSystem.requestShipChange does remove+set on
    // ShipType, but Zay-ES coalesces same-tick remove+set on a tracked
    // field into a single changed event, not added/removed.) The ship is
    // conceptually fresh, so reset live pools.
    for (final Entity spawned : ships.getAddedEntities()) {
      applyConfigTo(spawned, true);
    }
    // Changed: a watched component on the entity changed in place — most
    // commonly an ArenaId rewrite from ArenaMembershipSystem when a ship
    // crosses from arena A into arena B without going through void first.
    // Preserve live pools so a damaged ship doesn't get full health back.
    //
    // Exception: a ship-swap (AvatarSystem.requestShipChange does
    // remove+set on ShipType to force re-projection; Zay-ES coalesces to a
    // changed event, not added) stamps a ResetLivePool marker. Treat that
    // as a respawn so weapon `*CurrentLevel` starts get re-projected for
    // the new ship type. Clear the marker after one tick.
    for (final Entity changed : ships.getChangedEntities()) {
      final EntityId id = changed.getId();
      final boolean reset = ed.getComponent(id, ResetLivePool.class) != null;
      applyConfigTo(changed, reset);
      if (reset) {
        ed.removeComponent(id, ResetLivePool.class);
      }
    }
  }

  /**
   * Re-projects {@link ShipConfig} stats onto every ship currently in scope by
   * directly writing the latest component values. Use after a hot-reload of
   * the per-arena Groovy config so all ships pick up the new tuning, not just
   * whoever triggered the reload.
   *
   * <p>Live pools are preserved — this is a tuning reload, not a respawn, so a
   * mid-fight reload doesn't refill everyone's Energy or reset their
   * accumulated Thrust/Speed/Rotation cap upgrades.
   *
   * <p>Thread-safe to call from any thread (RMI, chat, sim) — this only
   * mutates ECS components via {@link EntityData#setComponent}, which is
   * already thread-safe in Zay-ES. The temporary {@code EntitySet} is
   * thread-local and released before return.
   *
   * @return number of ships re-projected
   */
  public int reprojectAll() {
    int n = 0;
    // (ShipType, ArenaId) — only ships actually in an arena have a config to project.
    // Ships in no-arena void are skipped by the filter; they pick up the new tuning
    // the next time they cross into an arena (handled by update()).
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
    // EntitySet filter guarantees ShipType + ArenaId presence on update()
    // entities, and reprojectAll filters on the same — defensive null
    // checks consolidated into a single short-circuit.
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

  /**
   * Diagnostic warn for the "ship type not configured for this arena" case.
   * Pulled out of {@link #applyConfigTo} to keep the dispatcher under the
   * cyclomatic threshold while preserving the operator-facing warn.
   */
  private static void warnMissingShipConfig(final ShipType shipType, final ArenaId arena) {
    if (log.isWarnEnabled()) {
      log.warn(
          "No ShipConfig for {} in arena {}; leaving defaults",
          shipType.getType(),
          arena.getArena());
    }
  }

  /**
   * Trailing log emit for {@link #applyConfigTo}. Pulled out so the validation
   * + project flow stays under the cyclomatic threshold; behaviour preserved
   * (info on every successful projection, debug stats line when debug enabled).
   * No fields read or projected here — purely diagnostic.
   */
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
    // Weapon / inventory projections — delegated to ShipWeaponsProjector to
    // keep the spawn system's class-level cyclomatic complexity bounded.
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
    // Status-family capabilities — delegated to ShipStatusProjector.
    ShipStatusProjector.projectCloak(ed, shipId, cfg.cloak(), resetLivePool);
    ShipStatusProjector.projectStealth(ed, shipId, cfg.stealth(), resetLivePool);
    ShipStatusProjector.projectXRadar(ed, shipId, cfg.xradar(), resetLivePool);
    ShipStatusProjector.projectAntiwarp(ed, shipId, cfg.antiwarp(), resetLivePool);
    projectRepellable(shipId, cfg.repellable());
  }

  /**
   * Slice S5 — toggle the {@link infinity.es.Repellable} marker per the
   * ship's template. Marker presence opts the ship into the repel-impulse
   * scan in {@code RepelSystem}.
   */
  private void projectRepellable(final EntityId shipId, final boolean repellable) {
    if (repellable) {
      ed.setComponent(shipId, new infinity.es.Repellable());
    } else {
      ed.removeComponent(shipId, infinity.es.Repellable.class);
    }
  }

  /** Project Thrust: reset live value on respawn only; Stats always re-project (mirrors Energy pattern). */
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

  /**
   * Pattern 4 + ADR 0001 split: {@link Energy} is the live pool
   * (depletes from damage / weapon costs, regens via the recharge rate
   * in {@link EnergyStats} up to {@code EnergyStats.max}). The bundled
   * {@link EnergyStats} record carries the upgradeable cap
   * ({@code max}), the absolute hard cap ({@code hardMax}), the per-
   * pickup increment ({@code upgrade}), and the three-tuple for the
   * recharge rate (current / max / upgrade in energy/sec — Subspace's
   * per-10-second integer converted at this projection boundary).
   *
   * <p><b>Respawn vs tuning semantics</b> (preserved from pre-ADR
   * {@code projectEnergy} + {@code projectRecharge}). On respawn
   * ({@code resetLivePool=true}) every field re-projects from
   * template and the live {@link Energy} pool resets. On tuning
   * reload ({@code resetLivePool=false}, fires on Groovy hot-reload
   * and arena cross), the {@code max} field (current effective cap)
   * is preserved so a player's accumulated cap upgrades survive the
   * reload — every other field re-projects from template. The live
   * {@link Energy} pool is also preserved on tuning so a damaged ship
   * doesn't free-heal.
   */
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
      // Tuning reload — preserve the current effective cap (matches
      // pre-ADR behaviour where Energy component survived reproject).
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
    // Blip name derived from the ship enum's canonical name (e.g. "ship_warbird")
    // so client-side blip-spatial registries can mirror SISpatialFactory's naming.
    ed.setComponent(shipId, RadarShapeInfo.create(cfg.type().getName() + "_blip", ed));
  }

  // Pattern-4 weapon/inventory and status-family projections live in
  // ShipWeaponsProjector / ShipStatusProjector — see {@link #project} for
  // the dispatch. Both projector classes are pure-static helpers in this
  // package; the boundary discipline (template→component projection happens
  // here, called only from this system) is preserved.
}
