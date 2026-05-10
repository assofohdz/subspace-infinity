// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.BombLevel;
import infinity.BulletLevel;
import infinity.Ship;
import infinity.config.BombStats;
import infinity.config.BulletStats;
import infinity.config.BurstStats;
import infinity.config.CountStats;
import infinity.config.CountWithDelayStats;
import infinity.config.MineStats;
import infinity.config.RocketStats;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.config.StatusStats;
import java.util.Map;

/**
 * Delegate for a {@code ship(Ship.X) { ... }} block in a {@code ships.groovy}
 * preset. Each stat method accepts a Groovy named-argument map and stores a
 * {@link ShipStat} (or weapon/inventory/status sub-record). Stats not called
 * stay at {@code (0, 0, 0)}; inventory/status blocks not declared stay
 * {@code null} (= "ship doesn't carry / can't acquire this item" per the B2
 * grilled-through plan in {@code .scratch/settings-pipeline-slices.md}).
 *
 * <p>Extracted from {@link GroovyShipLoader} per arch-review finding #10 — see
 * {@code .scratch/arch-review.md}. The builder is reused by
 * {@link GroovyShipLoader.ShipClosure} (DSL evaluator) and by tests that
 * exercise per-field projection without standing up the full {@code GroovyShell}
 * pipeline.
 */
final class ShipConfigBuilder {

  /**
   * Default per-second linear-damping coefficient used when a ship script
   * omits {@code linearDamping}. {@code 0.99} = 1% velocity loss per second
   * at typical operating speed (math fit against the historical
   * {@code dragFactor 0.05} coast-decay rate; see Slice S1 in
   * {@code .scratch/physics-audit.md}).
   */
  static final double DEFAULT_LINEAR_DAMPING = 0.99;

  /**
   * Default angular-velocity ease rate (1/sec) used when a ship script omits
   * {@code turnResponsiveness}. Matches the historical
   * {@code PlayerDriver.TURN_RESPONSIVENESS} global.
   */
  static final double DEFAULT_TURN_RESPONSIVENESS = 8.0;

  /**
   * Default wall-bounce restitution used when a ship script omits
   * {@code bounceRestitution}. Matches the historical perfectly-elastic
   * default in {@code ContactSystem.newContact}.
   */
  static final double DEFAULT_BOUNCE_RESTITUTION = 1.0;

  /**
   * Default radar radius (world units) used when a ship script omits
   * {@code radarRange}. Sized larger than {@code LocalViewState.viewRadius}
   * (5 leaves × 32 cells = 160 world units) so the radar reveals more than
   * the rendered world view.
   */
  static final double DEFAULT_RADAR_RANGE = 250.0;

  /**
   * Default repellable flag used when a ship script omits {@code repellable}.
   * Slice S5 — Subspace canon is "repels push every ship," so the absence of
   * an explicit toggle keeps that behaviour.
   */
  static final boolean DEFAULT_REPELLABLE = true;

  private final Ship type;
  private ShipStat rotation = new ShipStat(0, 0, 0);
  private ShipStat thrust = new ShipStat(0, 0, 0);
  private ShipStat speed = new ShipStat(0, 0, 0);
  private ShipStat recharge = new ShipStat(0, 0, 0);
  private ShipStat energy = new ShipStat(0, 0, 0);
  private double linearDamping = DEFAULT_LINEAR_DAMPING;
  private double turnResponsiveness = DEFAULT_TURN_RESPONSIVENESS;
  private double bounceRestitution = DEFAULT_BOUNCE_RESTITUTION;
  private double radarRange = DEFAULT_RADAR_RANGE;
  // Inventory fields default to null per the Q6 grilled-through decision
  // (.scratch/settings-pipeline-slices.md): an explicit `ship(Ship.X) { … }`
  // block disallows any inventory type whose block isn't declared. The
  // permissive `DEFAULT_*` constants on ShipFallback remain in use only by
  // FALLBACK (the snapshot installed when ships.groovy is missing or fails
  // to parse).
  private BombStats bombs;
  private BulletStats bullets;
  private MineStats mines;
  private BurstStats bursts;
  private CountWithDelayStats thors;
  private CountStats repels;
  private CountStats decoys;
  private CountStats bricks;
  private RocketStats rockets;
  private CountStats portals;
  private StatusStats cloak;
  private StatusStats stealth;
  private StatusStats xradar;
  private StatusStats antiwarp;
  private boolean repellable = DEFAULT_REPELLABLE;

  // Package-private so unit tests in this package can build configs without
  // standing up the full GroovyShell pipeline.
  ShipConfigBuilder(final Ship type) {
    this.type = type;
  }

  public void rotation(final Map<String, ?> args) {
    this.rotation = toStat("rotation", args);
  }

  public void thrust(final Map<String, ?> args) {
    this.thrust = toStat("thrust", args);
  }

  public void speed(final Map<String, ?> args) {
    this.speed = toStat("speed", args);
  }

  public void recharge(final Map<String, ?> args) {
    this.recharge = toStat("recharge", args);
  }

  public void energy(final Map<String, ?> args) {
    this.energy = toStat("energy", args);
  }

  public void linearDamping(final Number value) {
    this.linearDamping = doubleArg("linearDamping", value);
  }

  public void turnResponsiveness(final Number value) {
    this.turnResponsiveness = doubleArg("turnResponsiveness", value);
  }

  public void bounceRestitution(final Number value) {
    this.bounceRestitution = doubleArg("bounceRestitution", value);
  }

  public void radarRange(final Number value) {
    this.radarRange = doubleArg("radarRange", value);
  }

  /**
   * Slice S5 — when {@code true} (default), ship-spawn projection stamps
   * {@link infinity.es.Repellable} on the ship so a repel within range
   * pushes it away. Subspace canon: every ship is repellable. Set
   * {@code false} per ship to opt out (e.g. boss / heavy bot variants).
   */
  public void repellable(final boolean enabled) {
    this.repellable = enabled;
  }

  /**
   * {@code bombs start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 10,
   *        fireDelay: 25, speed: 2000, thrust: 400}
   *
   * <p>{@code speed} and {@code thrust} are in Subspace velocity units
   * (canonical {@code [Ship] BombSpeed} / {@code BombThrust} key
   * ranges). Fire-time consumer applies
   * {@code EngineConfig.subspaceVelocityScale} + cap to land in jME
   * world units. See slice 10 (speed) and S2 (thrust / recoil).
   */
  public void bombs(final Map<String, ?> args) {
    this.bombs =
        new BombStats(
            bombsArg("bombs", args, "start"),
            bombsArg("bombs", args, "max"),
            intArg("bombs", args, "cost"),
            longArg("bombs", args, "fireDelay"),
            intArg("bombs", args, "speed"),
            intArg("bombs", args, "thrust"));
  }

  /**
   * {@code bullets start: BulletLevel.LEVEL_1, max: BulletLevel.LEVEL_4, cost: 10,
   *        fireDelay: 25, speed: 2000}
   *
   * <p>{@code speed} is in Subspace velocity units (canonical
   * {@code [Ship] BulletSpeed} key range). Fire-time consumer applies
   * {@code EngineConfig.subspaceVelocityScale} + cap. See slice 10.
   */
  public void bullets(final Map<String, ?> args) {
    this.bullets =
        new BulletStats(
            bulletsArg("bullets", args, "start"),
            bulletsArg("bullets", args, "max"),
            intArg("bullets", args, "cost"),
            longArg("bullets", args, "fireDelay"),
            intArg("bullets", args, "speed"));
  }

  /**
   * {@code mines start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 50,
   *        fireDelay: 500, speed: 0}
   *
   * <p>{@code speed} is in Subspace velocity units. Default {@code 0} =
   * inert drop (mine drops dead-still, does not inherit ship velocity).
   * Fire-time consumer applies {@code EngineConfig.subspaceVelocityScale}
   * + cap to land in jME world units. Slice s7-mine-speed.
   *
   * <p>Infinity extension — see {@link MineStats#speed} for the
   * canon-divergence rationale (Subspace's {@code ## Mine} section does
   * not define a per-ship {@code MineSpeed} knob).
   */
  public void mines(final Map<String, ?> args) {
    this.mines =
        new MineStats(
            bombsArg("mines", args, "start"),
            bombsArg("mines", args, "max"),
            intArg("mines", args, "cost"),
            longArg("mines", args, "fireDelay"),
            intArg("mines", args, "speed"));
  }

  /**
   * {@code bursts start: 5, max: 5, speed: 3000}
   *
   * <p>{@code speed} is in Subspace velocity units (canonical
   * {@code [Ship] BurstSpeed} key range). Fire-time consumer applies
   * {@code EngineConfig.subspaceVelocityScale} + cap to land each
   * fan-projectile's launch speed in jME world units. See slice 10.
   */
  public void bursts(final Map<String, ?> args) {
    this.bursts =
        new BurstStats(
            intArg("bursts", args, "start"),
            intArg("bursts", args, "max"),
            intArg("bursts", args, "speed"));
  }

  /** {@code thors start: 2, max: 2, fireDelay: 1000} */
  public void thors(final Map<String, ?> args) {
    this.thors =
        new CountWithDelayStats(
            intArg("thors", args, "start"),
            intArg("thors", args, "max"),
            longArg("thors", args, "fireDelay"));
  }

  /** {@code repels start: 10, max: 20} */
  public void repels(final Map<String, ?> args) {
    this.repels =
        new CountStats(intArg("repels", args, "start"), intArg("repels", args, "max"));
  }

  /** {@code decoys start: 0, max: 1} */
  public void decoys(final Map<String, ?> args) {
    this.decoys =
        new CountStats(intArg("decoys", args, "start"), intArg("decoys", args, "max"));
  }

  /** {@code bricks start: 0, max: 1} */
  public void bricks(final Map<String, ?> args) {
    this.bricks =
        new CountStats(intArg("bricks", args, "start"), intArg("bricks", args, "max"));
  }

  /**
   * {@code rockets start: 0, max: 3, activeTimeCs: 100}
   *
   * <p>The third arg is Subspace per-ship {@code RocketTime} in
   * centiseconds — buff lifetime once the player fires a rocket. Stored
   * raw on {@link RocketStats}; {@code ShipSpawnSystem} converts to ms
   * when projecting onto the ship's {@code RocketTime} component.
   */
  public void rockets(final Map<String, ?> args) {
    this.rockets =
        new RocketStats(
            intArg("rockets", args, "start"),
            intArg("rockets", args, "max"),
            longArg("rockets", args, "activeTimeCs"));
  }

  /** {@code portals start: 0, max: 2} */
  public void portals(final Map<String, ?> args) {
    this.portals =
        new CountStats(intArg("portals", args, "start"), intArg("portals", args, "max"));
  }

  /**
   * {@code cloak status: 1, energy: 100}
   *
   * <p>Subspace per-ship Cloak capability — {@code status} tri-state
   * ({@code 0..2}) and {@code energy} drain rate ({@code 0..32000},
   * 1000ths-per-centisecond per REFERENCE.md). Stored raw on
   * {@link StatusStats}; {@code ShipSpawnSystem} projects to
   * {@link infinity.es.ship.toggles.CloakStatus} +
   * {@link infinity.es.ship.toggles.CloakEnergy} +
   * {@link infinity.es.ship.toggles.Cloak} components per the Status-
   * family applier rule.
   */
  public void cloak(final Map<String, ?> args) {
    this.cloak =
        new StatusStats(
            intArg("cloak", args, "status"), intArg("cloak", args, "energy"));
  }

  /**
   * {@code stealth status: 1, energy: 100}
   *
   * <p>Same shape as {@link #cloak}. Projects to
   * {@link infinity.es.ship.toggles.StealthStatus} +
   * {@link infinity.es.ship.toggles.StealthEnergy} +
   * {@link infinity.es.ship.toggles.Stealth}.
   */
  public void stealth(final Map<String, ?> args) {
    this.stealth =
        new StatusStats(
            intArg("stealth", args, "status"), intArg("stealth", args, "energy"));
  }

  /**
   * {@code xradar status: 1, energy: 100}
   *
   * <p>Same shape as {@link #cloak}. Projects to
   * {@link infinity.es.ship.toggles.XRadarStatus} +
   * {@link infinity.es.ship.toggles.XRadarEnergy} +
   * {@link infinity.es.ship.toggles.XRadar}.
   */
  public void xradar(final Map<String, ?> args) {
    this.xradar =
        new StatusStats(
            intArg("xradar", args, "status"), intArg("xradar", args, "energy"));
  }

  /**
   * {@code antiwarp status: 1, energy: 100}
   *
   * <p>Same shape as {@link #cloak}. Projects to
   * {@link infinity.es.ship.toggles.AntiwarpStatus} +
   * {@link infinity.es.ship.toggles.AntiwarpEnergy} +
   * {@link infinity.es.ship.toggles.Antiwarp}.
   *
   * <p>Note: arena-global {@code [Toggle] AntiWarpPixels} (range) and
   * {@code [Misc] AntiWarpSettleDelay} are separate concerns —
   * deferred to their own slices (polish-bag), not part of 6b's
   * per-ship Status-family scope.
   */
  public void antiwarp(final Map<String, ?> args) {
    this.antiwarp =
        new StatusStats(
            intArg("antiwarp", args, "status"), intArg("antiwarp", args, "energy"));
  }

  private static ShipStat toStat(final String statName, final Map<String, ?> args) {
    return new ShipStat(
        intArg(statName, args, "initial"),
        intArg(statName, args, "max"),
        intArg(statName, args, "upgrade"));
  }

  private static int intArg(
      final String statName, final Map<String, ?> args, final String key) {
    final Object v = args.get(key);
    if (v instanceof Number n) {
      return n.intValue();
    }
    throw new IllegalArgumentException(
        "Ship stat '" + statName + "' is missing numeric '" + key + "' (got " + v + ")");
  }

  private static long longArg(
      final String statName, final Map<String, ?> args, final String key) {
    final Object v = args.get(key);
    if (v instanceof Number n) {
      return n.longValue();
    }
    throw new IllegalArgumentException(
        "Ship stat '" + statName + "' is missing numeric '" + key + "' (got " + v + ")");
  }

  private static BombLevel bombsArg(
      final String statName, final Map<String, ?> args, final String key) {
    final Object v = args.get(key);
    if (v instanceof BombLevel b) {
      return b;
    }
    throw new IllegalArgumentException(
        "Ship stat '" + statName + "' '" + key + "' must be a BombLevel enum value (got " + v + ")");
  }

  private static BulletLevel bulletsArg(
      final String statName, final Map<String, ?> args, final String key) {
    final Object v = args.get(key);
    if (v instanceof BulletLevel g) {
      return g;
    }
    throw new IllegalArgumentException(
        "Ship stat '" + statName + "' '" + key + "' must be a BulletLevel enum value (got " + v + ")");
  }

  private static double doubleArg(final String fieldName, final Number value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Ship feel '" + fieldName + "' requires a numeric value (got null)");
    }
    return value.doubleValue();
  }

  ShipConfig build() {
    return new ShipConfig(
        type,
        rotation,
        thrust,
        speed,
        recharge,
        energy,
        linearDamping,
        turnResponsiveness,
        bounceRestitution,
        radarRange,
        bombs,
        bullets,
        mines,
        bursts,
        thors,
        repels,
        decoys,
        bricks,
        rockets,
        portals,
        cloak,
        stealth,
        xradar,
        antiwarp,
        repellable);
  }
}
