// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelChange;
import infinity.es.ship.actions.RepelStats;

/** Canonical writer for live {@link Repel} count. All drain/clamp logic lives in {@link BaseInventoryCountSystem}; this subclass just binds the three types + the count constructor. Split from {@link RepelSystem} so it can register before DecaySystem. */
public class RepelCountSystem extends BaseInventoryCountSystem<Repel, RepelChange, RepelStats> {

  public RepelCountSystem() {
    super(Repel.class, RepelChange.class, RepelStats.class, count -> new Repel(count));
  }
}
