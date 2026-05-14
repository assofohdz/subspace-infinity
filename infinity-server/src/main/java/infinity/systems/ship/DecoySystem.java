// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyChange;
import infinity.es.ship.actions.DecoyStats;

/** Canonical writer for live {@link Decoy} (count). All drain/clamp logic lives in {@link BaseInventoryCountSystem}; this subclass just binds the three types + the count constructor. */
public class DecoySystem extends BaseInventoryCountSystem<Decoy, DecoyChange, DecoyStats> {

  public DecoySystem() {
    super(Decoy.class, DecoyChange.class, DecoyStats.class, count -> new Decoy(count));
  }
}
