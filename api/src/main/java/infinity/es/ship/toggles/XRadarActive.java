// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import infinity.es.ActiveToggle;

/** Live x-radar on/off toggle; flipped by {@link XRadarActiveChange}. See ADR 0001 + REFERENCE.md {@code ## XRadar}. */
public class XRadarActive implements ActiveToggle {

  private final boolean active;

  public XRadarActive() {
    this(false);
  }

  public XRadarActive(final boolean active) {
    this.active = active;
  }

  @Override
  public boolean isActive() {
    return active;
  }
}
