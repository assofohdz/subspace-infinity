// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.Ship;
import infinity.sim.ArenaModule;

/** Per-arena ship allow/deny gate. {@code AvatarSystem} consults the active impl before applying a {@code ShipTypeChange}. */
public interface RosterModule extends ArenaModule {

  /** {@code true} = ship type is allowed in this arena. Default: permit everything. */
  default boolean isShipAllowed(final Ship ship) {
    return true;
  }
}
