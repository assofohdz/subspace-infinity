// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

/** Additive count delta to live {@link Burst}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code BurstSystem}. See ADR 0001. */
public record BurstChange(int delta) implements DeltaChange {

  public BurstChange() {
    this(0);
  }
}
