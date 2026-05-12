// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/** Per-ship cloak tier + active drain rate (energy/sec). Tri-state per REFERENCE.md {@code ## Cloak}. See ADR 0001. */
public record CloakStats(int statusTier, double energyDrainPerSecond) implements EntityComponent {

  public CloakStats() {
    this(0, 0.0);
  }
}
