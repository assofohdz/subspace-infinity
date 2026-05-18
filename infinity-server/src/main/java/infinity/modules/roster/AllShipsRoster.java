// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.roster;

import infinity.modules.ModuleContext;
import infinity.modules.RosterModule;

/** Zero-config — every ship type is allowed. Inherits the {@code RosterModule.isShipAllowed} default. */
public final class AllShipsRoster implements RosterModule {

  @SuppressWarnings("PMD.UnusedFormalParameter") // ctor signature required by ModuleLoader
  public AllShipsRoster(final ModuleContext ctx) {
    // intentionally empty — zero-config marker module
  }
}
