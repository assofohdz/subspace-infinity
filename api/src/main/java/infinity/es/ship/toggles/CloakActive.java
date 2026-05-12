// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/** Live cloak on/off toggle; flipped by {@link CloakActiveChange}. See ADR 0001 + REFERENCE.md {@code ## Cloak}. */
public class CloakActive implements EntityComponent {

  private final boolean active;

  public CloakActive() {
    this(false);
  }

  public CloakActive(final boolean active) {
    this.active = active;
  }

  public boolean isActive() {
    return active;
  }
}
