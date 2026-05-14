// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.toggles.AntiwarpActive;
import infinity.es.ship.toggles.AntiwarpActiveChange;

/** Canonical writer for {@link AntiwarpActive}. All drain/revert logic lives in {@link BaseToggleSystem}; this subclass binds the two types + the active-component constructor. */
public class AntiwarpSystem extends BaseToggleSystem<AntiwarpActive, AntiwarpActiveChange> {

  public AntiwarpSystem() {
    super(AntiwarpActive.class, AntiwarpActiveChange.class, active -> new AntiwarpActive(active));
  }
}
