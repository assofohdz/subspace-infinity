// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.config.BombStats;
import infinity.config.BulletStats;
import infinity.config.BurstStats;
import infinity.config.CountStats;
import infinity.config.CountWithDelayStats;
import infinity.config.MineStats;
import infinity.config.RocketStats;
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
import infinity.es.ship.actions.RocketTime;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorMaxCount;
import infinity.es.ship.weapons.BombCost;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombMaxLevel;
import infinity.es.ship.weapons.BombSpeed;
import infinity.es.ship.weapons.BombThrust;
import infinity.es.ship.weapons.BulletCost;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.BulletMaxLevel;
import infinity.es.ship.weapons.BulletSpeed;
import infinity.es.ship.weapons.BurstSpeed;
import infinity.es.ship.weapons.MineCost;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineMaxLevel;
import infinity.es.ship.weapons.MineSpeed;
import javax.annotation.Nullable;

/**
 * Pattern-4 spawn-projection helpers for the weapon / inventory families.
 * Extracted from {@link ShipSpawnSystem} as pure static methods so the
 * sum-of-method cyclomatic complexity on the spawn system stays under PMD's
 * class-level threshold while preserving the single Pattern-4 boundary
 * (template→component projection happens here, called only by
 * {@code ShipSpawnSystem.project}).
 *
 * <p>Each method follows the same Pattern-4 split: the live "current
 * count / level" component resets only on {@code resetLivePool == true} so
 * mid-fight Groovy reloads don't refill ammo or revoke earned upgrades, while
 * capability components ({@code *Max}, {@code *Cost}, {@code *FireDelay},
 * speed/thrust knobs) always re-project so a tuning edit takes effect
 * immediately.
 *
 * <p>Each method also guards against a null stats record so a {@code
 * ShipConfig} can express "this ship doesn't carry bombs / bullets / mines /
 * bursts / thors / repels / decoys / bricks / rockets / portals" by setting
 * the field to {@code null}. The corresponding {@code *Max} component is
 * then absent on the ship, which prize appliers interpret as "not allowed"
 * (component-absence as the disallow signal).
 *
 * <p>Package-private; not part of any public API. The methods take
 * {@link EntityData} as their first argument because that's the only piece
 * of {@code ShipSpawnSystem} state they read — making them static keeps the
 * spawn system's per-method CC from accumulating into the class-level total.
 */
final class ShipWeaponsProjector {

  private ShipWeaponsProjector() {
    // utility class — instantiation prevented
  }

  static void projectBombs(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final BombStats bombs,
      final boolean resetLivePool) {
    if (bombs == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new BombCurrentLevel(bombs.start()));
    }
    ed.setComponent(shipId, new BombMaxLevel(bombs.max()));
    ed.setComponent(shipId, new BombCost(bombs.cost()));
    ed.setComponent(shipId, new BombFireDelay(bombs.fireDelayCs()));
    ed.setComponent(shipId, new BombSpeed(bombs.speed()));
    ed.setComponent(shipId, new BombThrust(bombs.thrust()));
  }

  static void projectBullets(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final BulletStats bullets,
      final boolean resetLivePool) {
    if (bullets == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new BulletCurrentLevel(bullets.start()));
    }
    ed.setComponent(shipId, new BulletMaxLevel(bullets.max()));
    ed.setComponent(shipId, new BulletCost(bullets.cost()));
    ed.setComponent(shipId, new BulletFireDelay(bullets.fireDelayCs()));
    ed.setComponent(shipId, new BulletSpeed(bullets.speed()));
  }

  static void projectMines(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final MineStats mines,
      final boolean resetLivePool) {
    if (mines == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new MineCurrentLevel(mines.start()));
    }
    ed.setComponent(shipId, new MineMaxLevel(mines.max()));
    ed.setComponent(shipId, new MineCost(mines.cost()));
    ed.setComponent(shipId, new MineFireDelay(mines.fireDelayCs()));
    ed.setComponent(shipId, new MineSpeed(mines.speed()));
  }

  static void projectBursts(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final BurstStats bursts,
      final boolean resetLivePool) {
    if (bursts == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Burst(bursts.start()));
    }
    ed.setComponent(shipId, new BurstMax(bursts.max()));
    ed.setComponent(shipId, new BurstSpeed(bursts.speed()));
  }

  static void projectThors(
      final EntityData ed,
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

  static void projectRepels(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final CountStats repels,
      final boolean resetLivePool) {
    if (repels == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Repel(repels.start()));
    }
    ed.setComponent(shipId, new RepelMax(repels.max()));
  }

  static void projectDecoys(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final CountStats decoys,
      final boolean resetLivePool) {
    if (decoys == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Decoy(decoys.start()));
    }
    ed.setComponent(shipId, new DecoyMax(decoys.max()));
  }

  static void projectBricks(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final CountStats bricks,
      final boolean resetLivePool) {
    if (bricks == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Brick(bricks.start()));
    }
    ed.setComponent(shipId, new BrickMax(bricks.max()));
  }

  static void projectRockets(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final RocketStats rockets,
      final boolean resetLivePool) {
    if (rockets == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Rocket(rockets.start()));
    }
    ed.setComponent(shipId, new RocketMax(rockets.max()));
    // Per-ship buff lifetime (Subspace [Ship] RocketTime, centiseconds → ms).
    // Read at fire-time by ConsumableSystem to compute the buff entity's
    // Decay deadline.
    ed.setComponent(shipId, new RocketTime(rockets.activeTimeCs() * 10L));
  }

  static void projectPortals(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final CountStats portals,
      final boolean resetLivePool) {
    if (portals == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Portal(portals.start()));
    }
    ed.setComponent(shipId, new PortalMax(portals.max()));
  }
}
