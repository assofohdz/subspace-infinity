// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/** Live antiwarp on/off toggle; flipped by {@link AntiwarpActiveChange}. See ADR 0001 + REFERENCE.md {@code ## Antiwarp}. */
public class AntiwarpActive implements EntityComponent {

  private final boolean active;

  public AntiwarpActive() {
    this(false);
  }

  public AntiwarpActive(final boolean active) {
    this.active = active;
  }

  public boolean isActive() {
    return active;
  }
}
