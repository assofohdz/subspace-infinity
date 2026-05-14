// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import infinity.es.DeltaChange;

/** Additive delta to live {@link Energy}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code EnergySystem}. See ADR 0001. */
public record EnergyChange(int delta) implements DeltaChange {

  public EnergyChange() {
    this(0);
  }
}
