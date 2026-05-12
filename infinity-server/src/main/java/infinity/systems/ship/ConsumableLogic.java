// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import infinity.config.BrickConfig;
import infinity.config.DecoyConfig;
import infinity.config.PortalConfig;
import infinity.config.RepelConfig;
import infinity.config.RocketConfig;
import infinity.config.ThorConfig;
import infinity.es.arena.ArenaId;
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.settings.ConfigRegistrySystem;

/** Per-family config lookups + can-fire/can-place gates for {@link ConsumableSystem}. */
public final class ConsumableLogic {

  private ConsumableLogic() {}

  public static ThorConfig thorConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return ThorConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).thor();
  }

  public static RepelConfig repelConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return RepelConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).repel();
  }

  public static RocketConfig rocketConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return RocketConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).rocket();
  }

  public static BrickConfig brickConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return BrickConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).brick();
  }

  public static DecoyConfig decoyConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return DecoyConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).decoy();
  }

  public static PortalConfig portalConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return PortalConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).portal();
  }

  /** Thor adds a fire-delay gate; the others are count-only (Subspace canon). */
  public static boolean canFireThor(
      final EntityData ed, final EntitySet thorOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (thorOwners.containsId(requesterId)) {
      final ThorCurrentCount tcc = ed.getComponent(requesterId, ThorCurrentCount.class);
      final ThorFireDelay tfd = ed.getComponent(requesterId, ThorFireDelay.class);
      return tcc.getCount() > 0 && tfd.getPercent() >= 1;
    }
    return false;
  }

  public static boolean canFireRepel(
      final EntityData ed, final EntitySet repelOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!repelOwners.containsId(requesterId)) {
      return false;
    }
    final Repel curr = ed.getComponent(requesterId, Repel.class);
    return curr != null && curr.getCount() > 0;
  }

  public static boolean canFireRocket(
      final EntityData ed, final EntitySet rocketOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!rocketOwners.containsId(requesterId)) {
      return false;
    }
    final Rocket curr = ed.getComponent(requesterId, Rocket.class);
    return curr != null && curr.getCount() > 0;
  }

  public static boolean canPlaceBrick(
      final EntityData ed, final EntitySet brickOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!brickOwners.containsId(requesterId)) {
      return false;
    }
    final Brick curr = ed.getComponent(requesterId, Brick.class);
    return curr != null && curr.getCount() > 0;
  }

  public static boolean canPlaceDecoy(
      final EntityData ed, final EntitySet decoyOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!decoyOwners.containsId(requesterId)) {
      return false;
    }
    final Decoy curr = ed.getComponent(requesterId, Decoy.class);
    return curr != null && curr.getCount() > 0;
  }

  public static boolean canPlacePortal(
      final EntityData ed, final EntitySet portalOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!portalOwners.containsId(requesterId)) {
      return false;
    }
    final Portal curr = ed.getComponent(requesterId, Portal.class);
    return curr != null && curr.getCount() > 0;
  }
}
