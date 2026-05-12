// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Additive delta to live {@link Energy}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code EnergySystem}. See ADR 0001. */
public record EnergyChange(int delta) implements EntityComponent {

  public EnergyChange() {
    this(0);
  }
}
