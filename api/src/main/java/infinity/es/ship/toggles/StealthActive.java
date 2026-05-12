// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/** Live stealth on/off toggle; flipped by {@link StealthActiveChange}. See ADR 0001 + REFERENCE.md {@code ## Stealth}. */
public class StealthActive implements EntityComponent {

  private final boolean active;

  public StealthActive() {
    this(false);
  }

  public StealthActive(final boolean active) {
    this.active = active;
  }

  public boolean isActive() {
    return active;
  }
}
