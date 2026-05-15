// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Closure;
import infinity.Ship;
import infinity.config.ShipRestrictionsConfig;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Delegate for the {@code shipRestrictions { allow…; deny…; maxPerTeam… }} block inside {@code arena{…}}. Infinity-only — no Subspace canon. */
public final class GroovyShipRestrictionsAdapter {

  private static final String BLOCK = "shipRestrictions";

  private final Set<Ship> allowed = EnumSet.noneOf(Ship.class);
  private final Set<Ship> denied = EnumSet.noneOf(Ship.class);
  private final Map<Ship, Integer> maxPerTeam = new EnumMap<>(Ship.class);

  GroovyShipRestrictionsAdapter() {}

  /** Test seam — apply a Groovy DSL block against a fresh accumulator. */
  public static ShipRestrictionsConfig evaluate(final Closure<?> body) {
    final GroovyShipRestrictionsAdapter block = new GroovyShipRestrictionsAdapter();
    body.setDelegate(block);
    body.setResolveStrategy(Closure.DELEGATE_FIRST);
    body.call();
    return block.build();
  }

  /** {@code allow Ship.WARBIRD, Ship.JAVELIN} — variadic; null entries reject. */
  public void allow(final Object... ships) {
    addEach(allowed, "allow", ships);
  }

  /** {@code allow([Ship.WARBIRD, Ship.JAVELIN])} — list form. */
  public void allow(final List<?> ships) {
    addEach(allowed, "allow", ships.toArray());
  }

  /** {@code deny Ship.SPIDER} — variadic. */
  public void deny(final Object... ships) {
    addEach(denied, "deny", ships);
  }

  /** {@code deny([Ship.SPIDER])} — list form. */
  public void deny(final List<?> ships) {
    addEach(denied, "deny", ships.toArray());
  }

  /** {@code maxPerTeam Ship.WARBIRD, 4} — single per-call mapping. */
  public void maxPerTeam(final Object ship, final Number cap) {
    final Ship key = coerceShip("maxPerTeam", ship);
    if (cap == null) {
      throw new IllegalArgumentException(BLOCK + ".maxPerTeam requires a numeric cap for " + ship);
    }
    maxPerTeam.put(key, Integer.valueOf(cap.intValue()));
  }

  /** {@code maxPerTeam([(Ship.WARBIRD): 4, (Ship.SPIDER): 2])} — map form. */
  public void maxPerTeam(final Map<?, ?> entries) {
    for (final Map.Entry<?, ?> e : entries.entrySet()) {
      final Ship key = coerceShip("maxPerTeam", e.getKey());
      if (!(e.getValue() instanceof Number n)) {
        throw new IllegalArgumentException(
            BLOCK + ".maxPerTeam value for " + e.getKey() + " must be numeric (got "
                + e.getValue() + ")");
      }
      maxPerTeam.put(key, Integer.valueOf(n.intValue()));
    }
  }

  ShipRestrictionsConfig build() {
    return new ShipRestrictionsConfig(allowed, denied, maxPerTeam);
  }

  private static void addEach(final Set<Ship> target, final String op, final Object[] ships) {
    if (ships == null) {
      throw new IllegalArgumentException(BLOCK + "." + op + " requires at least one ship");
    }
    for (final Object o : ships) {
      target.add(coerceShip(op, o));
    }
  }

  private static Ship coerceShip(final String op, final Object o) {
    if (o instanceof Ship ship) {
      return ship;
    }
    if (o instanceof String s) {
      return Ship.getShip(s);
    }
    if (o instanceof Number n) {
      return Ship.getShip((byte) n.intValue());
    }
    throw new IllegalArgumentException(
        BLOCK + "." + op + " expects a Ship enum / name / byte id (got " + o + ")");
  }
}
