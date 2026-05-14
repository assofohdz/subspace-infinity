// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import infinity.es.ToggleChange;

/** Value-replacement payload for {@link AntiwarpActive}; drained by {@code AntiwarpSystem}. See ADR 0001. */
public record AntiwarpActiveChange(boolean newValue) implements ToggleChange {

  public AntiwarpActiveChange() {
    this(false);
  }
}
