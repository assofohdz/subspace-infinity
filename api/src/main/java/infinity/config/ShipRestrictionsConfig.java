// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.Ship;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-arena ship gating: optional allow / deny sets + per-ship max-per-team caps.
 * Infinity-only divergence — REFERENCE.md has no {@code [Team]} canon and no
 * {@code EnterShipEnergy}; the full-energy gate is enforced by {@code AvatarSystem}.
 *
 * @see infinity.config // template tier, ADR-0002
 */
public record ShipRestrictionsConfig(
    Set<Ship> allowed, Set<Ship> denied, Map<Ship, Integer> maxPerTeam) {

  /** Empty fallback — every ship is allowed, no per-team caps. */
  public static final ShipRestrictionsConfig DEFAULTS =
      new ShipRestrictionsConfig(EnumSet.noneOf(Ship.class), EnumSet.noneOf(Ship.class), Map.of());

  public ShipRestrictionsConfig {
    Objects.requireNonNull(allowed, "allowed");
    Objects.requireNonNull(denied, "denied");
    Objects.requireNonNull(maxPerTeam, "maxPerTeam");
    allowed = copyShipSet(allowed);
    denied = copyShipSet(denied);
    maxPerTeam = copyShipMap(maxPerTeam);
  }

  /** Allow-list wins: a non-empty {@link #allowed} restricts to that set; otherwise {@link #denied} forbids. */
  public boolean isAllowed(final Ship ship) {
    Objects.requireNonNull(ship, "ship");
    if (!allowed.isEmpty()) {
      return allowed.contains(ship);
    }
    return !denied.contains(ship);
  }

  /** {@code -1} = unrestricted (no per-team cap configured for this ship). */
  public int maxPerTeam(final Ship ship) {
    Objects.requireNonNull(ship, "ship");
    final Integer cap = maxPerTeam.get(ship);
    return cap == null ? -1 : cap.intValue();
  }

  private static Set<Ship> copyShipSet(final Set<Ship> in) {
    if (in.isEmpty()) {
      return Set.of();
    }
    final Set<Ship> copy = EnumSet.copyOf(in);
    return Set.copyOf(copy);
  }

  private static Map<Ship, Integer> copyShipMap(final Map<Ship, Integer> in) {
    if (in.isEmpty()) {
      return Map.of();
    }
    final Map<Ship, Integer> copy = new EnumMap<>(Ship.class);
    copy.putAll(in);
    return Map.copyOf(copy);
  }
}
