// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Marker for ship entities driven by AI rather than a network-connected player. Positive
 * counterpart to {@link PlayerShip} — replaces the older "bot = absence of PlayerShip"
 * inverse pattern. Stamped by {@code AIEntities.createMobShip} at spawn time.
 *
 * <p>An identity refactor (sequential counter, etc.) is deferred; today this is a pure
 * marker.
 */
public final class BotShip implements EntityComponent {

  public BotShip() {
    // marker
  }
}
