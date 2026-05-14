// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.toggles.XRadarActive;
import infinity.es.ship.toggles.XRadarActiveChange;

/** Canonical writer for {@link XRadarActive}. All drain/revert logic lives in {@link BaseToggleSystem}; this subclass binds the two types + the active-component constructor. */
public class XRadarSystem extends BaseToggleSystem<XRadarActive, XRadarActiveChange> {

  public XRadarSystem() {
    super(XRadarActive.class, XRadarActiveChange.class, active -> new XRadarActive(active));
  }
}
