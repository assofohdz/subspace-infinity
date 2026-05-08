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

/**
 * Stateless helpers for {@link ConsumableSystem} — per-family config lookups and
 * gate checks. Extracted from {@code ConsumableSystem} to keep the host class's
 * cyclomatic-complexity sum under PMD's class threshold without fragmenting
 * Pattern-4 spawn projection (the host system retains all
 * {@code create*}/{@code deductCostOf*} state-mutating methods so component
 * projection stays in one place).
 *
 * <p>All methods are {@code public static}; callers pass the {@link EntityData}
 * and the relevant {@link EntitySet}/{@link ConfigRegistrySystem} explicitly so
 * this class holds no state.
 */
public final class ConsumableLogic {

  private ConsumableLogic() {
    // utility class
  }

  // -- Config lookups -----------------------------------------------------
  // Each lookup falls back to the type's `DEFAULTS` snapshot when the
  // attacker has no ArenaId (legacy spawn paths) or the arena has no
  // ConfigRegistry entry yet.

  /** Per-arena Thor tuning; {@link ThorConfig#DEFAULTS} fallback. */
  public static ThorConfig thorConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return ThorConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).thor();
  }

  /** Per-arena Repel tuning; {@link RepelConfig#DEFAULTS} fallback. */
  public static RepelConfig repelConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return RepelConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).repel();
  }

  /** Per-arena Rocket tuning; {@link RocketConfig#DEFAULTS} fallback. */
  public static RocketConfig rocketConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return RocketConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).rocket();
  }

  /** Per-arena Brick tuning; {@link BrickConfig#DEFAULTS} fallback. */
  public static BrickConfig brickConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return BrickConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).brick();
  }

  /** Per-arena Decoy tuning; {@link DecoyConfig#DEFAULTS} fallback. */
  public static DecoyConfig decoyConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return DecoyConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).decoy();
  }

  /** Per-arena Portal tuning; {@link PortalConfig#DEFAULTS} fallback. */
  public static PortalConfig portalConfigFor(
      final EntityData ed, final ConfigRegistrySystem configRegistry, final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return PortalConfig.DEFAULTS;
    }
    return configRegistry.forArena(arenaId).portal();
  }

  // -- Gate checks --------------------------------------------------------
  // Each gate confirms the requester is in the per-family owner EntitySet
  // (i.e. has the inventory + per-ship cap components projected at spawn)
  // and that at least one charge is available. Subspace canon — no
  // fire-delay component except for Thor.

  /** Thor firing gate: owner in {@code thorOwners}, count &gt; 0, fire-delay elapsed. */
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

  /** Repel firing gate: owner in {@code repelOwners} with at least one charge. */
  public static boolean canFireRepel(
      final EntityData ed, final EntitySet repelOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!repelOwners.containsId(requesterId)) {
      return false;
    }
    final Repel curr = ed.getComponent(requesterId, Repel.class);
    return curr != null && curr.getCount() > 0;
  }

  /** Rocket firing gate: owner in {@code rocketOwners} with at least one charge. */
  public static boolean canFireRocket(
      final EntityData ed, final EntitySet rocketOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!rocketOwners.containsId(requesterId)) {
      return false;
    }
    final Rocket curr = ed.getComponent(requesterId, Rocket.class);
    return curr != null && curr.getCount() > 0;
  }

  /** Brick placement gate: owner in {@code brickOwners} with at least one charge. */
  public static boolean canPlaceBrick(
      final EntityData ed, final EntitySet brickOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!brickOwners.containsId(requesterId)) {
      return false;
    }
    final Brick curr = ed.getComponent(requesterId, Brick.class);
    return curr != null && curr.getCount() > 0;
  }

  /** Decoy placement gate: owner in {@code decoyOwners} with at least one charge. */
  public static boolean canPlaceDecoy(
      final EntityData ed, final EntitySet decoyOwners, final Entity requester) {
    final EntityId requesterId = requester.getId();
    if (!decoyOwners.containsId(requesterId)) {
      return false;
    }
    final Decoy curr = ed.getComponent(requesterId, Decoy.class);
    return curr != null && curr.getCount() > 0;
  }

  /** Portal placement gate: owner in {@code portalOwners} with at least one charge. */
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
