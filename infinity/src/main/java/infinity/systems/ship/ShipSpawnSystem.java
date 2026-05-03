// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.config.BombStats;
import infinity.config.CountStats;
import infinity.config.CountWithDelayStats;
import infinity.config.GunStats;
import infinity.config.MineStats;
import javax.annotation.Nullable;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.RadarShapeInfo;
import infinity.es.arena.ArenaId;
import infinity.es.ship.BounceRestitution;
import infinity.es.ship.DragFactor;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyMax;
import infinity.es.ship.EnergyUpgrade;
import infinity.es.ship.Health;
import infinity.es.ship.RadarRange;
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
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickMax;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstMax;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyMax;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalMax;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelMax;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketMax;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorMaxCount;
import infinity.es.ship.weapons.BombCost;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombMaxLevel;
import infinity.es.ship.weapons.GunCost;
import infinity.es.ship.weapons.GunCurrentLevel;
import infinity.es.ship.weapons.GunFireDelay;
import infinity.es.ship.weapons.GunMaxLevel;
import infinity.es.ship.weapons.MineCost;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineMaxLevel;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;

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
public class ShipSpawnSystem extends AbstractGameSystem {

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
    ed = getSystem(EntityData.class);
    configRegistry = getSystem(ConfigRegistrySystem.class);

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

    // Added: ship just gained both ShipType and ArenaId — spawn-into-arena,
    // re-entry from no-arena void, or ship-swap (AvatarSystem remove+set on
    // ShipType surfaces here). The ship is conceptually fresh, so reset live
    // pools (Health/Energy + current Thrust/Speed/Rotation/Recharge).
    for (final Entity spawned : ships.getAddedEntities()) {
      applyConfigTo(spawned, true);
    }
    // Changed: a watched component on the entity changed in place — most
    // commonly an ArenaId rewrite from ArenaMembershipSystem when a ship
    // crosses from arena A into arena B without going through void first.
    // Preserve live pools so a damaged ship doesn't get full health back.
    for (final Entity changed : ships.getChangedEntities()) {
      applyConfigTo(changed, false);
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
    final ShipType shipType = shipEntity.get(ShipType.class);
    if (shipType == null || shipType.getType() == null) {
      log.warn("Ship {} has null ShipType; skipping config projection", shipEntity.getId());
      return;
    }

    // Read the ship's own ArenaId — the EntitySet filter guarantees presence
    // for entities surfaced via update(), and reprojectAll filters on it too.
    final ArenaId arena = shipEntity.get(ArenaId.class);
    if (arena == null) {
      return;
    }

    final ConfigRegistry snapshot = configRegistry.forArena(arena);
    final ShipConfig cfg = snapshot.getShip(shipType.getType());
    if (cfg == null) {
      log.warn(
          "No ShipConfig for {} in arena {}; leaving defaults",
          shipType.getType(),
          arena.getArena());
      return;
    }

    project(shipEntity.getId(), cfg, resetLivePool);
    log.info(
        "Projected ShipConfig {} ({}) from arena {} onto ship {}",
        shipType.getType(),
        resetLivePool ? "respawn" : "tuning",
        arena.getArena(),
        shipEntity.getId());
    if (log.isDebugEnabled()) {
      log.debug(
          "  stats: thrust={} speed={} rotation={} recharge={} energy={} drag={} turn={} bounce={}",
          cfg.thrust(),
          cfg.speed(),
          cfg.rotation(),
          cfg.recharge(),
          cfg.energy(),
          cfg.dragFactor(),
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
    projectBombs(shipId, cfg.bombs(), resetLivePool);
    projectGuns(shipId, cfg.guns(), resetLivePool);
    projectMines(shipId, cfg.mines(), resetLivePool);
    projectBursts(shipId, cfg.bursts(), resetLivePool);
    projectThors(shipId, cfg.thors(), resetLivePool);
    projectRepels(shipId, cfg.repels(), resetLivePool);
    projectDecoys(shipId, cfg.decoys(), resetLivePool);
    projectBricks(shipId, cfg.bricks(), resetLivePool);
    projectRockets(shipId, cfg.rockets(), resetLivePool);
    projectPortals(shipId, cfg.portals(), resetLivePool);
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
    ed.setComponent(shipId, new DragFactor(cfg.dragFactor()));
    ed.setComponent(shipId, new TurnResponsiveness(cfg.turnResponsiveness()));
    ed.setComponent(shipId, new BounceRestitution(cfg.bounceRestitution()));
  }

  private void projectRadar(final EntityId shipId, final ShipConfig cfg) {
    ed.setComponent(shipId, new RadarRange(cfg.radarRange()));
    // Blip name derived from the ship enum's canonical name (e.g. "ship_warbird")
    // so client-side blip-spatial registries can mirror SISpatialFactory's naming.
    ed.setComponent(shipId, RadarShapeInfo.create(cfg.type().getName() + "_blip", ed));
  }

  // Weapon / inventory projections — same Pattern-4 split as Energy:
  // the live "current count / level" component resets only on respawn so a
  // mid-fight Groovy reload doesn't refill ammo or revoke earned upgrades;
  // capability components (max, cost, fire-delay) always re-project so a
  // tuning edit takes effect immediately.

  // Each weapon/inventory projection block guards against a null stat so a
  // ShipConfig can express "this ship doesn't carry bombs / guns / mines /
  // bursts / thors / repels" by setting the field to null. The corresponding
  // *Max component is then absent on the ship, which prize appliers
  // interpret as "not allowed" (component-absence as the disallow signal).

  private void projectBombs(
      final EntityId shipId, @Nullable final BombStats bombs, final boolean resetLivePool) {
    if (bombs == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new BombCurrentLevel(bombs.start()));
    }
    ed.setComponent(shipId, new BombMaxLevel(bombs.max()));
    ed.setComponent(shipId, new BombCost(bombs.cost()));
    ed.setComponent(shipId, new BombFireDelay(bombs.fireDelayCs()));
  }

  private void projectGuns(
      final EntityId shipId, @Nullable final GunStats guns, final boolean resetLivePool) {
    if (guns == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new GunCurrentLevel(guns.start()));
    }
    ed.setComponent(shipId, new GunMaxLevel(guns.max()));
    ed.setComponent(shipId, new GunCost(guns.cost()));
    ed.setComponent(shipId, new GunFireDelay(guns.fireDelayCs()));
  }

  private void projectMines(
      final EntityId shipId, @Nullable final MineStats mines, final boolean resetLivePool) {
    if (mines == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new MineCurrentLevel(mines.start()));
    }
    ed.setComponent(shipId, new MineMaxLevel(mines.max()));
    ed.setComponent(shipId, new MineCost(mines.cost()));
    ed.setComponent(shipId, new MineFireDelay(mines.fireDelayCs()));
  }

  private void projectBursts(
      final EntityId shipId, @Nullable final CountStats bursts, final boolean resetLivePool) {
    if (bursts == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Burst(bursts.start()));
    }
    ed.setComponent(shipId, new BurstMax(bursts.max()));
  }

  private void projectThors(
      final EntityId shipId,
      @Nullable final CountWithDelayStats thors,
      final boolean resetLivePool) {
    if (thors == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new ThorCurrentCount(thors.start()));
    }
    ed.setComponent(shipId, new ThorMaxCount(thors.max()));
    ed.setComponent(shipId, new ThorFireDelay(thors.fireDelayCs()));
  }

  private void projectRepels(
      final EntityId shipId, @Nullable final CountStats repels, final boolean resetLivePool) {
    if (repels == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Repel(repels.start()));
    }
    ed.setComponent(shipId, new RepelMax(repels.max()));
  }

  private void projectDecoys(
      final EntityId shipId, @Nullable final CountStats decoys, final boolean resetLivePool) {
    if (decoys == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Decoy(decoys.start()));
    }
    ed.setComponent(shipId, new DecoyMax(decoys.max()));
  }

  private void projectBricks(
      final EntityId shipId, @Nullable final CountStats bricks, final boolean resetLivePool) {
    if (bricks == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Brick(bricks.start()));
    }
    ed.setComponent(shipId, new BrickMax(bricks.max()));
  }

  private void projectRockets(
      final EntityId shipId, @Nullable final CountStats rockets, final boolean resetLivePool) {
    if (rockets == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Rocket(rockets.start()));
    }
    ed.setComponent(shipId, new RocketMax(rockets.max()));
  }

  private void projectPortals(
      final EntityId shipId, @Nullable final CountStats portals, final boolean resetLivePool) {
    if (portals == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Portal(portals.start()));
    }
    ed.setComponent(shipId, new PortalMax(portals.max()));
  }
}
