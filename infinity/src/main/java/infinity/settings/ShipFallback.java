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

/**
 * Built-in fallback {@link ConfigRegistry} snapshot installed when an arena's
 * {@code ships.groovy} is missing or fails to evaluate. Covers all 8 ships
 * with SVS-canonical tuning (matches the legacy {@code conf/svs/ship-*}
 * fragments) so picking any ship in an unconfigured arena spawns a working
 * ship rather than an inert zero-stat one. Feel knobs (drag / turn / bounce)
 * use the {@code DEFAULT_*} constants on {@link ShipConfigBuilder}, which
 * match the historical Java globals.
 *
 * <p>Extracted from {@link GroovyShipLoader} per arch-review finding #10 — see
 * {@code .scratch/arch-review.md}. Holds two concerns:
 *
 * <ul>
 *   <li>Permissive {@code DEFAULT_*} weapon / inventory stat constants used
 *       only by the FALLBACK snapshot. Authored {@code ships.groovy} blocks
 *       leave omitted inventory fields {@code null} (the disallow signal);
 *       these defaults exist to keep an unconfigured arena playable.
 *   <li>The {@link #FALLBACK} snapshot itself — one {@link ShipConfig} per
 *       {@link Ship} enum value.
 * </ul>
 *
 * <p><strong>Drift discipline</strong> — these values are intentionally
 * SVS-canonical, NOT a mirror of any per-arena {@code ships.groovy} preset
 * (e.g. {@code trench-04-2026/ships.groovy}). Per-arena presets diverge
 * gameplay-wise; FALLBACK keeps the legacy SVS baseline so unconfigured
 * arenas are predictable. Adjust only when the SVS baseline itself moves.
 */
final class ShipFallback {

  // --- Defaults for the per-ship weapon / inventory stat groups ---------
  // Match the values previously inlined in ShipFactory.createShip.
  // Preserves prior behaviour for any preset whose ships.groovy doesn't
  // override these.

  /** Default starting bomb level + max + cost + fire-delay + speed + thrust. */
  static final BombStats DEFAULT_BOMBS =
      new BombStats(
          BombLevel.BOMB_1,
          BombLevel.BOMB_4,
          /* cost */ 10,
          /* fireDelayCs */ 25,
          /* speed */ 2000, // SVS canon BombSpeed=2000 (Subspace velocity units)
          /* thrust */ 0); // No recoil by default; presets opt in (SVS canon = 400)

  /** Default starting bullet level + max + cost + fire-delay + speed. */
  static final BulletStats DEFAULT_GUNS =
      new BulletStats(
          BulletLevel.LEVEL_1,
          BulletLevel.LEVEL_4,
          /* cost */ 10,
          /* fireDelayCs */ 25,
          /* speed */ 2000); // SVS canon BulletSpeed=2000

  /** Default starting mine level + max + cost + fire-delay + speed. */
  static final MineStats DEFAULT_MINES =
      new MineStats(
          BombLevel.BOMB_1,
          BombLevel.BOMB_4,
          /* cost */ 50,
          /* fireDelayCs */ 500,
          /* speed */ 0); // Inert drop — slice s7-mine-speed; arenas opt in to kicker mines

  /** Default starting + max burst inventory count + per-projectile speed. */
  static final BurstStats DEFAULT_BURSTS =
      new BurstStats(/* start */ 5, /* max */ 5, /* speed */ 3000); // SVS canon BurstSpeed=3000

  /** Default starting + max thor inventory count + per-fire delay. */
  static final CountWithDelayStats DEFAULT_THORS =
      new CountWithDelayStats(/* start */ 2, /* max */ 2, /* fireDelayCs */ 1000);

  /** Default starting + max repel inventory count. */
  static final CountStats DEFAULT_REPELS = new CountStats(/* start */ 10, /* max */ 20);

  /**
   * Default decoy/brick/rocket/portal inventory: {@code null} = "ship doesn't
   * carry / can't acquire this item." Per the B2 grilled-through plan
   * (see {@code .scratch/settings-pipeline-slices.md}), the typed pipeline
   * treats {@code null} as the disallow signal — the corresponding
   * {@code *Max} component isn't projected, and the prize applier no-ops.
   * Presets that want decoys/bricks/rockets/portals must declare them
   * explicitly in {@code ships.groovy}.
   */
  static final CountStats DEFAULT_DECOYS = null;

  /** See {@link #DEFAULT_DECOYS}. */
  static final CountStats DEFAULT_BRICKS = null;

  /** See {@link #DEFAULT_DECOYS}. */
  static final RocketStats DEFAULT_ROCKETS = null;

  /** See {@link #DEFAULT_DECOYS}. */
  static final CountStats DEFAULT_PORTALS = null;

  /**
   * Built-in fallback snapshot installed when an arena's {@code ships.groovy}
   * is missing or fails to evaluate. See class Javadoc for the SVS-canonical
   * baseline rationale.
   */
  static final ConfigRegistry FALLBACK =
      ConfigRegistry.builder()
          .ship(Ship.WARBIRD, fallbackShip(
              Ship.WARBIRD,
              /* rotation */ new ShipStat(210, 300, 40),
              /* thrust */   new ShipStat(16,  19,  2),
              /* speed */    new ShipStat(2010, 3250, 250),
              /* recharge */ new ShipStat(400, 1150, 166),
              /* energy */   new ShipStat(1000, 1700, 100)))
          .ship(Ship.JAVELIN, fallbackShip(
              Ship.JAVELIN,
              /* rotation */ new ShipStat(200, 230, 40),
              /* thrust */   new ShipStat(15,  17,  2),
              /* speed */    new ShipStat(2200, 3750, 250),
              /* recharge */ new ShipStat(400, 1150, 166),
              /* energy */   new ShipStat(1000, 1700, 100)))
          .ship(Ship.SPIDER, fallbackShip(
              Ship.SPIDER,
              /* rotation */ new ShipStat(200, 230, 40),
              /* thrust */   new ShipStat(15,  17,  2),
              /* speed */    new ShipStat(2010, 3250, 250),
              /* recharge */ new ShipStat(500, 1150, 166),
              /* energy */   new ShipStat(1000, 1700, 100)))
          .ship(Ship.LEVIATHAN, fallbackShip(
              Ship.LEVIATHAN,
              /* rotation */ new ShipStat(200, 230, 40),
              /* thrust */   new ShipStat(15,  17,  2),
              /* speed */    new ShipStat(2010, 3250, 250),
              /* recharge */ new ShipStat(400, 1150, 166),
              /* energy */   new ShipStat(1000, 1700, 100)))
          .ship(Ship.TERRIER, fallbackShip(
              Ship.TERRIER,
              /* rotation */ new ShipStat(200, 230, 40),
              /* thrust */   new ShipStat(15,  17,  2),
              /* speed */    new ShipStat(2010, 3250, 250),
              /* recharge */ new ShipStat(400, 1150, 166),
              /* energy */   new ShipStat(1000, 1700, 100)))
          .ship(Ship.WEASEL, fallbackShip(
              Ship.WEASEL,
              /* rotation */ new ShipStat(200, 230, 40),
              /* thrust */   new ShipStat(15,  17,  2),
              /* speed */    new ShipStat(2010, 3250, 250),
              /* recharge */ new ShipStat(400, 1150, 166),
              /* energy */   new ShipStat(1000, 1700, 100)))
          .ship(Ship.LANCASTER, fallbackShip(
              Ship.LANCASTER,
              /* rotation */ new ShipStat(200, 230, 40),
              /* thrust */   new ShipStat(15,  17,  2),
              /* speed */    new ShipStat(2010, 3250, 250),
              /* recharge */ new ShipStat(400, 1150, 166),
              /* energy */   new ShipStat(1000, 1700, 100)))
          .ship(Ship.SHARK, fallbackShip(
              Ship.SHARK,
              /* rotation */ new ShipStat(200, 230, 40),
              /* thrust */   new ShipStat(15,  17,  2),
              /* speed */    new ShipStat(2010, 3250, 250),
              /* recharge */ new ShipStat(400, 1150, 166),
              /* energy */   new ShipStat(1000, 1750, 100)))
          .build();

  private ShipFallback() {
    // Static holder; not instantiable.
  }

  private static ShipConfig fallbackShip(
      final Ship type,
      final ShipStat rotation,
      final ShipStat thrust,
      final ShipStat speed,
      final ShipStat recharge,
      final ShipStat energy) {
    return new ShipConfig(
        type,
        rotation,
        thrust,
        speed,
        recharge,
        energy,
        ShipConfigBuilder.DEFAULT_LINEAR_DAMPING,
        ShipConfigBuilder.DEFAULT_TURN_RESPONSIVENESS,
        ShipConfigBuilder.DEFAULT_BOUNCE_RESTITUTION,
        ShipConfigBuilder.DEFAULT_RADAR_RANGE,
        DEFAULT_BOMBS,
        DEFAULT_GUNS,
        DEFAULT_MINES,
        DEFAULT_BURSTS,
        DEFAULT_THORS,
        DEFAULT_REPELS,
        DEFAULT_DECOYS,
        DEFAULT_BRICKS,
        DEFAULT_ROCKETS,
        DEFAULT_PORTALS,
        /* cloak */ null,
        /* stealth */ null,
        /* xradar */ null,
        /* antiwarp */ null,
        ShipConfigBuilder.DEFAULT_REPELLABLE);
  }
}
