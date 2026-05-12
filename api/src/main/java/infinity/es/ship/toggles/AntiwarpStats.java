// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/** Per-ship antiwarp tier + active drain rate (energy/sec). Tri-state per REFERENCE.md {@code ## Antiwarp}. See ADR 0001. */
public record AntiwarpStats(int statusTier, double energyDrainPerSecond) implements EntityComponent {

  public AntiwarpStats() {
    this(0, 0.0);
  }
}
