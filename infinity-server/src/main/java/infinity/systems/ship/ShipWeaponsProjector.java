// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.config.CountStats;
import infinity.config.CountWithDelayStats;
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickStats;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstStats;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyStats;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalStats;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelStats;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketStats;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorStats;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombStats;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.BulletStats;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineStats;
import javax.annotation.Nullable;

/**
 * Pattern-4 spawn-projection helpers for the weapon / inventory families.
 * Called only by {@code ShipSpawnSystem.project}; see {@link ShipSpawnSystem}
 * class Javadoc for the projector-split map (which projector owns which aspect
 * family). Projects each
 * {@code *Config}-tier template (e.g. {@link infinity.config.BombStats}) into
 * the component-tier {@code *Stats} record (e.g. {@link BombStats}) plus the
 * matching live-pool component (e.g. {@link BombCurrentLevel}). Live pools
 * are reset only on {@code resetLivePool == true} so mid-fight Groovy
 * reloads preserve earned upgrades and current ammo.
 *
 * <p>Per-instance cooldown components ({@code *FireDelay}) are re-stamped on
 * every projection (the constructor zeroes the start nanos) so a respawned
 * ship can fire immediately; runtime re-stamping after each fire lives in
 * {@link WeaponsEligibility}.
 *
 * <p>See ADR 0001 §"Continuous + Stats split"; the older scattered
 * {@code *MaxLevel} / {@code *Cost} / {@code *Speed} / {@code *Thrust}
 * components were bundled into the per-aspect {@code *Stats} record in
 * Wave 4a, with the inventory aspects (Brick/Decoy/Portal/Repel/Rocket/Thor)
 * following the same pattern in Wave 4b.
 */
final class ShipWeaponsProjector {

  private ShipWeaponsProjector() {
    // utility class — instantiation prevented
  }

  static void projectBombs(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final infinity.config.BombStats bombs,
      final boolean resetLivePool) {
    if (bombs == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new BombCurrentLevel(bombs.start()));
    }
    final long fireDelayMillis = bombs.fireDelayCs() * 10L;
    ed.setComponent(
        shipId,
        new BombStats(bombs.max(), bombs.cost(), fireDelayMillis, bombs.speed(), bombs.thrust()));
    ed.setComponent(shipId, new BombFireDelay(fireDelayMillis));
  }

  static void projectBullets(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final infinity.config.BulletStats bullets,
      final boolean resetLivePool) {
    if (bullets == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new BulletCurrentLevel(bullets.start()));
    }
    final long fireDelayMillis = bullets.fireDelayCs() * 10L;
    ed.setComponent(
        shipId,
        new BulletStats(bullets.max(), bullets.cost(), fireDelayMillis, bullets.speed()));
    ed.setComponent(shipId, new BulletFireDelay(fireDelayMillis));
  }

  static void projectMines(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final infinity.config.MineStats mines,
      final boolean resetLivePool) {
    if (mines == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new MineCurrentLevel(mines.start()));
    }
    final long fireDelayMillis = mines.fireDelayCs() * 10L;
    ed.setComponent(
        shipId,
        new MineStats(mines.max(), mines.cost(), fireDelayMillis, mines.speed()));
    ed.setComponent(shipId, new MineFireDelay(fireDelayMillis));
  }

  static void projectBursts(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final infinity.config.BurstStats bursts,
      final boolean resetLivePool) {
    if (bursts == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Burst(bursts.start()));
    }
    ed.setComponent(shipId, new BurstStats(bursts.max(), bursts.speed()));
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
    final long fireDelayMillis = thors.fireDelayCs() * 10L;
    ed.setComponent(shipId, new ThorStats(thors.max(), fireDelayMillis));
    ed.setComponent(shipId, new ThorFireDelay(fireDelayMillis));
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
    ed.setComponent(shipId, new RepelStats(repels.max()));
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
    ed.setComponent(shipId, new DecoyStats(decoys.max()));
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
    ed.setComponent(shipId, new BrickStats(bricks.max()));
  }

  static void projectRockets(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final infinity.config.RocketStats rockets,
      final boolean resetLivePool) {
    if (rockets == null) {
      return;
    }
    if (resetLivePool) {
      ed.setComponent(shipId, new Rocket(rockets.start()));
    }
    // Per-ship buff lifetime (Subspace [Ship] RocketTime, centiseconds → ms).
    final long buffDurationMillis = rockets.activeTimeCs() * 10L;
    ed.setComponent(shipId, new RocketStats(rockets.max(), buffDurationMillis));
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
    ed.setComponent(shipId, new PortalStats(portals.max()));
  }
}
