// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Marker on the rocket-buff entity (parented to ship); pairs with {@link RocketSnapshot}. {@code Decay} owns lifetime; {@code RocketBuffSystem} reverts on removal. */
public class RocketBuff implements EntityComponent {

  public RocketBuff() {}

  @Override
  public String toString() {
    return "RocketBuff[]";
  }
}
