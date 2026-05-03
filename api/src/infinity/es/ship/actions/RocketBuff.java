// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Marker on the buff entity itself (parent = ship). Lifecycle is owned
 * by the buff entity's {@link com.simsilica.es.common.Decay}; the
 * canonical decay reaper deletes the entity at the deadline, which
 * triggers {@code RocketBuffSystem.onRemoved} to revert the ship.
 *
 * <p>Pairs with {@link RocketSnapshot} (revert data) and
 * {@link com.simsilica.es.common.Parent} (link to the ship being
 * buffed) on the same buff entity.
 */
public class RocketBuff implements EntityComponent {

  public RocketBuff() {}

  @Override
  public String toString() {
    return "RocketBuff[]";
  }
}
