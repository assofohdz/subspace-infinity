// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.actions.ThorChange;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorStats;

/** Canonical writer for live {@link ThorCurrentCount}. All drain/clamp logic lives in {@link BaseInventoryCountSystem}; this subclass just binds the three types + the count constructor. */
public class ThorSystem extends BaseInventoryCountSystem<ThorCurrentCount, ThorChange, ThorStats> {

  public ThorSystem() {
    super(ThorCurrentCount.class, ThorChange.class, ThorStats.class, count -> new ThorCurrentCount(count));
  }
}
