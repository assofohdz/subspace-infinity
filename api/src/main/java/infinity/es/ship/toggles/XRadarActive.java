// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/** Live x-radar on/off toggle; flipped by {@link XRadarActiveChange}. See ADR 0001 + REFERENCE.md {@code ## XRadar}. */
public class XRadarActive implements EntityComponent {

  private final boolean active;

  public XRadarActive() {
    this(false);
  }

  public XRadarActive(final boolean active) {
    this.active = active;
  }

  public boolean isActive() {
    return active;
  }
}
