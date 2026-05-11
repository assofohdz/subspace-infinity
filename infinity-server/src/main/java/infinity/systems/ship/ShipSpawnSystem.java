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
import infinity.es.ship.EnergyMax;
import infinity.es.ship.EnergyUpgrade;
import infinity.es.ship.Health;
import infinity.es.ship.RadarRange;
import infinity.es.ship.ResetLivePool;
import infinity.es.ship.Recharge;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.RechargeUpgrade;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.RotationUpgrade;
import infinity.es.ship.ShipType;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.SpeedUpgrade;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;
import infinity.es.ship.ThrustUpgrade;
import infinity.es.ship.TurnResponsiveness;
import infinity.es.ship.actions.RocketBuffIntent;
import infinity.settings.ConfigRegistrySystem;
import infinity.systems.BaseInfinitySystem;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * resource pools ({@link Health} and {@link Energy}) are reset to
 * {@code stat.initial()}:
 * <ul>
 *   <li><b>Respawn projection</b> (everything resets) — fires on
 *       {@code getAddedEntities}: spawn-into-arena, re-entry from void, and
 *       ship-swap (AvatarSystem remove+set on ShipType surfaces here). The
 *       ship is conceptually "fresh", so a full reset is correct.
 *   <li><b>Tuning projection</b> (everything except Health/Energy resets) —
 *       fires on {@code getChangedEntities} (arena cross while alive) and on
 *       {@link #reprojectAll} (Groovy hot-reload). Capability stats (Thrust,
 *       Speed, Rotation, Recharge), their {@code *Max} / {@code *Upgrade}
 *       caps, and the feel knobs all pick up the new config — those stats
 *       don't deplete from gameplay so the user expects a Groovy edit to
 *       take effect immediately. Health and Energy are deliberately
 *       preserved so a damaged ship doesn't free-heal on arena cross or
 *       Groovy hot-reload.
 * </ul>
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
  /**
   * Pending {@link RocketBuffIntent} entities — emitted by
   * {@code ConsumableSystem} (activate) and {@code RocketBuffSystem}
   * (revert) and drained here. Single-writer entry-point for
   * rocket-buff-driven {@link Thrust} / {@link Speed} writes; see
   * {@code .claude/rules/replacement-as-mutation.md} (BACKLOG C1).
   */
  private EntitySet rocketIntents;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);

    // Watch ships that are currently in some arena. Ships without ArenaId
    // (no-arena void) are intentionally not in the set; they get reprojected
    // as soon as ArenaMembershipSystem assigns an ArenaId on entry.
    ships = ed.getEntities(ShipType.class, ArenaId.class);
    rocketIntents = ed.getEntities(RocketBuffIntent.class);
  }

  @Override
  protected void terminate() {
    ships.release();
    ships = null;
    rocketIntents.release();
    rocketIntents = null;
  }

  @Override
  public void update(final SimTime time) {
    ships.applyChanges();

    // Added: ship just gained both ShipType and ArenaId — spawn-into-arena
    // or re-entry from no-arena void. (Ship-swap surfaces in the changed
    // branch below: AvatarSystem.requestShipChange does remove+set on
    // ShipType, but Zay-ES coalesces same-tick remove+set on a tracked
    // field into a single changed event, not added/removed.) The ship is
    // conceptually fresh, so reset live pools (Health/Energy + current
    // Thrust/Speed/Rotation/Recharge + weapon `*CurrentLevel` starts).
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

    // RaM canonical drain for rocket-buff Thrust/Speed writes (BACKLOG C1).
    // Runs AFTER the template-projection branches so the drain wins on
    // same-tick reproject + buff race — deterministic outcome:
    // intent-wins. See .claude/rules/replacement-as-mutation.md.
    drainRocketBuffIntents();
  }

  /**
   * Drain pending {@link RocketBuffIntent} entities — fold per target
   * ship (last-by-entity-id wins per RaM rule #7), write the resulting
   * {@link Thrust} / {@link Speed}, and consume each intent entity.
   *
   * <p>Zay-ES iterates an {@link EntitySet} in monotonically-increasing
   * {@link EntityId} order, so inserting into a {@link LinkedHashMap}
   * keyed by target gives last-wins folding naturally: the
   * later-emitted intent (higher EntityId) overwrites the earlier one
   * for the same target. This is the deterministic resolution for the
   * rare same-tick activate + revert race.
   */
  private void drainRocketBuffIntents() {
    rocketIntents.applyChanges();
    if (rocketIntents.isEmpty()) {
      return;
    }
    final Map<EntityId, RocketBuffIntent> foldedByTarget = new LinkedHashMap<>();
    for (final Entity intentEntity : rocketIntents) {
      final RocketBuffIntent intent = intentEntity.get(RocketBuffIntent.class);
      if (intent == null || intent.getTarget() == null) {
        continue;
      }
      foldedByTarget.put(intent.getTarget(), intent);
    }
    for (final Map.Entry<EntityId, RocketBuffIntent> entry : foldedByTarget.entrySet()) {
      final EntityId target = entry.getKey();
      final RocketBuffIntent intent = entry.getValue();
      ed.setComponent(target, new Thrust(intent.getThrust()));
      ed.setComponent(target, new Speed(intent.getSpeed()));
    }
    // Consume intent entities — fire-and-forget shape mirrors
    // EnergySystem's HealthChange drain (Buff entity deleted after fold).
    for (final Entity intentEntity : rocketIntents) {
      ed.removeEntity(intentEntity.getId());
    }
  }

  /**
   * Re-projects {@link ShipConfig} stats onto every ship currently in scope by
   * directly writing the latest component values. Use after a hot-reload of
   * the per-arena Groovy config so all ships pick up the new tuning, not just
   * whoever triggered the reload.
   *
   * <p>Live pools are preserved — this is a tuning reload, not a respawn, so a
   * mid-fight reload doesn't refill everyone's Health/Energy.
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
    projectThrust(shipId, cfg.thrust());
    projectSpeed(shipId, cfg.speed());
    projectRotation(shipId, cfg.rotation());
    projectRecharge(shipId, cfg.recharge());
    projectEnergy(shipId, cfg.energy(), resetLivePool);
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

  // Capability stats — Thrust/Speed/Rotation/Recharge — always re-project from
  // the current config. They don't deplete from gameplay (PrizeSystem can lift
  // them via upgrades, but there's no "drain" path), so a Groovy edit is the
  // user's expected channel for changing them and should always take effect.

  private void projectThrust(final EntityId shipId, final ShipStat stat) {
    ed.setComponent(shipId, new Thrust(stat.initial()));
    ed.setComponent(shipId, new ThrustMax(stat.max()));
    ed.setComponent(shipId, new ThrustUpgrade(stat.upgrade()));
  }

  private void projectSpeed(final EntityId shipId, final ShipStat stat) {
    ed.setComponent(shipId, new Speed(stat.initial()));
    ed.setComponent(shipId, new SpeedMax(stat.max()));
    ed.setComponent(shipId, new SpeedUpgrade(stat.upgrade()));
  }

  private void projectRotation(final EntityId shipId, final ShipStat stat) {
    ed.setComponent(shipId, new Rotation(stat.initial() * ROTATION_UNITS_TO_RAD_SEC));
    ed.setComponent(shipId, new RotationMax(stat.max() * ROTATION_UNITS_TO_RAD_SEC));
    ed.setComponent(shipId, new RotationUpgrade(stat.upgrade() * ROTATION_UNITS_TO_RAD_SEC));
  }

  private void projectRecharge(final EntityId shipId, final ShipStat stat) {
    ed.setComponent(shipId, new Recharge(stat.initial() * RECHARGE_UNITS_TO_PER_SEC));
    ed.setComponent(shipId, new RechargeMax(stat.max() * RECHARGE_UNITS_TO_PER_SEC));
    ed.setComponent(shipId, new RechargeUpgrade(stat.upgrade() * RECHARGE_UNITS_TO_PER_SEC));
  }

  private void projectEnergy(
      final EntityId shipId, final ShipStat stat, final boolean resetLivePool) {
    // Pattern 4 split: Health is the live pool (depletes from damage / weapon
    // costs, regens via Recharge up to Energy); Energy is the upgradeable cap;
    // EnergyMax is the absolute hard cap on Energy.
    if (resetLivePool) {
      ed.setComponent(shipId, new Health(stat.initial()));
      ed.setComponent(shipId, new Energy(stat.initial()));
    }
    ed.setComponent(shipId, new EnergyMax(stat.max()));
    ed.setComponent(shipId, new EnergyUpgrade(stat.upgrade()));
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
