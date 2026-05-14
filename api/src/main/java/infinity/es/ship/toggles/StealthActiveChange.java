// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import infinity.es.ToggleChange;

/** Value-replacement payload for {@link StealthActive}; drained by {@code StealthSystem}. See ADR 0001. */
public record StealthActiveChange(boolean newValue) implements ToggleChange {

  public StealthActiveChange() {
    this(false);
  }
}
