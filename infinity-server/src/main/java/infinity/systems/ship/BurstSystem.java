// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstChange;
import infinity.es.ship.actions.BurstStats;

/** Canonical writer for live {@link Burst} (count). All drain/clamp logic lives in {@link BaseInventoryCountSystem}; this subclass just binds the three types + the count constructor. */
public class BurstSystem extends BaseInventoryCountSystem<Burst, BurstChange, BurstStats> {

  public BurstSystem() {
    super(Burst.class, BurstChange.class, BurstStats.class, count -> new Burst(count));
  }
}
