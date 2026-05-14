// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalChange;
import infinity.es.ship.actions.PortalStats;

/** Canonical writer for live {@link Portal} (count). All drain/clamp logic lives in {@link BaseInventoryCountSystem}; this subclass just binds the three types + the count constructor. */
public class PortalSystem extends BaseInventoryCountSystem<Portal, PortalChange, PortalStats> {

  public PortalSystem() {
    super(Portal.class, PortalChange.class, PortalStats.class, count -> new Portal(count));
  }
}
