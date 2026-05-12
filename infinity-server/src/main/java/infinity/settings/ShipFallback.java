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

/** SVS-canonical fallback {@link ConfigRegistry} installed when an arena's {@code ships.groovy} is missing/broken. Don't drift to per-arena presets — adjust only when SVS baseline moves. */
final class ShipFallback {

  static final BombStats DEFAULT_BOMBS =
      new BombStats(
          BombLevel.BOMB_1,
          BombLevel.BOMB_4,
          /* cost */ 10,
          /* fireDelayCs */ 25,
          /* speed */ 2000, // SVS canon BombSpeed=2000
          /* thrust */ 0); // SVS canon = 400; opt-in to avoid recoil by default.

  static final BulletStats DEFAULT_GUNS =
      new BulletStats(
          BulletLevel.LEVEL_1,
          BulletLevel.LEVEL_4,
          /* cost */ 10,
          /* fireDelayCs */ 25,
          /* speed */ 2000); // SVS canon BulletSpeed=2000

  static final MineStats DEFAULT_MINES =
      new MineStats(
          BombLevel.BOMB_1,
          BombLevel.BOMB_4,
          /* cost */ 50,
          /* fireDelayCs */ 500,
          /* speed */ 0); // Inert drop; arenas opt in to kicker mines.

  static final BurstStats DEFAULT_BURSTS =
      new BurstStats(/* start */ 5, /* max */ 5, /* speed */ 3000); // SVS canon BurstSpeed=3000

  static final CountWithDelayStats DEFAULT_THORS =
      new CountWithDelayStats(/* start */ 2, /* max */ 2, /* fireDelayCs */ 1000);

  static final CountStats DEFAULT_REPELS = new CountStats(/* start */ 10, /* max */ 20);

  // null = "ship doesn't carry / can't acquire" — the disallow signal in the typed pipeline.
  static final CountStats DEFAULT_DECOYS = null;

  static final CountStats DEFAULT_BRICKS = null;

  static final RocketStats DEFAULT_ROCKETS = null;

  static final CountStats DEFAULT_PORTALS = null;

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
