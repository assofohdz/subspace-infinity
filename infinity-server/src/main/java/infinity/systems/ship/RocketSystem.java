// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketChange;
import infinity.es.ship.actions.RocketStats;

/** Canonical writer for live {@link Rocket} (inventory count). All drain/clamp logic lives in {@link BaseInventoryCountSystem}; this subclass just binds the three types + the count constructor. */
public class RocketSystem extends BaseInventoryCountSystem<Rocket, RocketChange, RocketStats> {

  public RocketSystem() {
    super(Rocket.class, RocketChange.class, RocketStats.class, count -> new Rocket(count));
  }
}
