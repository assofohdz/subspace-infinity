// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.toggles.StealthActive;
import infinity.es.ship.toggles.StealthActiveChange;

/** Canonical writer for {@link StealthActive}. All drain/revert logic lives in {@link BaseToggleSystem}; this subclass binds the two types + the active-component constructor. */
public class StealthSystem extends BaseToggleSystem<StealthActive, StealthActiveChange> {

  public StealthSystem() {
    super(StealthActive.class, StealthActiveChange.class, active -> new StealthActive(active));
  }
}
