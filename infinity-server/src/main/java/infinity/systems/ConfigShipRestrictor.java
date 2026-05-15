// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.ShapeInfo;
import infinity.Ship;
import infinity.config.ShipRestrictionsConfig;
import infinity.es.arena.ArenaId;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.sim.ShipRestrictor;
import infinity.sim.util.InfinityRunTimeException;

/** Reads per-arena {@link ShipRestrictionsConfig} for {@code allow}/{@code deny} + {@code maxPerTeam} gating. Infinity-only — no Subspace canon. */
public final class ConfigShipRestrictor implements ShipRestrictor {

  private final ConfigRegistrySystem registry;
  private final AvatarSystem avatars;
  private final EntityData ed;
  private final EntityId arenaShipEntity;

  /**
   * @param arenaShipEntity ship being switched — used to resolve {@link ArenaId} for per-arena lookup;
   *     a {@code null} {@code ArenaId} (no membership yet) collapses to {@link ShipRestrictionsConfig#DEFAULTS}.
   */
  public ConfigShipRestrictor(
      final ConfigRegistrySystem registry,
      final AvatarSystem avatars,
      final EntityData ed,
      final EntityId arenaShipEntity) {
    this.registry = registry;
    this.avatars = avatars;
    this.ed = ed;
    this.arenaShipEntity = arenaShipEntity;
  }

  @Override
  public boolean canSwitch(final EntityId p, final byte ship, final int team) {
    final Ship target;
    try {
      target = Ship.getShip(ship);
    } catch (final InfinityRunTimeException unknown) { // spec / id 0 — let other gates decide
      return true;
    }
    final ShipRestrictionsConfig cfg = resolveConfig();
    if (!cfg.isAllowed(target)) {
      return false;
    }
    final int cap = cfg.maxPerTeam(target);
    if (cap < 0) {
      return true;
    }
    final ShapeInfo probe = ShapeInfo.create(target.getName(), 0.0, ed);
    return avatars.getShipCount(team, probe) < cap;
  }

  @Override
  public boolean canSwap(final EntityId p1, final EntityId p2, final int team) {
    return true;
  }

  @Override
  public byte fallbackShip() {
    return UNRESTRICTED;
  }

  private ShipRestrictionsConfig resolveConfig() {
    if (arenaShipEntity == null) {
      return ShipRestrictionsConfig.DEFAULTS;
    }
    final ArenaId arenaId = ed.getComponent(arenaShipEntity, ArenaId.class);
    if (arenaId == null) {
      return ShipRestrictionsConfig.DEFAULTS;
    }
    final ConfigRegistry snapshot = registry.forArena(arenaId);
    return snapshot.shipRestrictions();
  }
}
