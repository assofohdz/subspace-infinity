// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakActiveChange;

/** Canonical writer for {@link CloakActive}. All drain/revert logic lives in {@link BaseToggleSystem}; this subclass binds the two types + the active-component constructor. */
public class CloakSystem extends BaseToggleSystem<CloakActive, CloakActiveChange> {

  public CloakSystem() {
    super(CloakActive.class, CloakActiveChange.class, active -> new CloakActive(active));
  }
}
