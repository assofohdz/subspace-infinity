// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import infinity.es.ActiveToggle;

/** Live cloak on/off toggle; flipped by {@link CloakActiveChange}. See ADR 0001 + REFERENCE.md {@code ## Cloak}. */
public class CloakActive implements ActiveToggle {

  private final boolean active;

  public CloakActive() {
    this(false);
  }

  public CloakActive(final boolean active) {
    this.active = active;
  }

  @Override
  public boolean isActive() {
    return active;
  }
}
