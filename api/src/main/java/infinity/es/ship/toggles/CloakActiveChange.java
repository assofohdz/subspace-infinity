// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/** Value-replacement payload for {@link CloakActive}; drained by {@code CloakSystem}. See ADR 0001. */
public record CloakActiveChange(boolean newValue) implements EntityComponent {

  public CloakActiveChange() {
    this(false);
  }
}
