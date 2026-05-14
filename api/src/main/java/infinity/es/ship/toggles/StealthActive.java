// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import infinity.es.ActiveToggle;

/** Live stealth on/off toggle; flipped by {@link StealthActiveChange}. See ADR 0001 + REFERENCE.md {@code ## Stealth}. */
public class StealthActive implements ActiveToggle {

  private final boolean active;

  public StealthActive() {
    this(false);
  }

  public StealthActive(final boolean active) {
    this.active = active;
  }

  @Override
  public boolean isActive() {
    return active;
  }
}
