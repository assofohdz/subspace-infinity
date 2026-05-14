// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickChange;
import infinity.es.ship.actions.BrickStats;

/** Canonical writer for live {@link Brick} (count). All drain/clamp logic lives in {@link BaseInventoryCountSystem}; this subclass just binds the three types + the count constructor. */
public class BrickSystem extends BaseInventoryCountSystem<Brick, BrickChange, BrickStats> {

  public BrickSystem() {
    super(Brick.class, BrickChange.class, BrickStats.class, count -> new Brick(count));
  }
}
