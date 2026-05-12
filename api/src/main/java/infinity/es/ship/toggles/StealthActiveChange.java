// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/** Value-replacement payload for {@link StealthActive}; drained by {@code StealthSystem}. See ADR 0001. */
public record StealthActiveChange(boolean newValue) implements EntityComponent {

  public StealthActiveChange() {
    this(false);
  }
}
