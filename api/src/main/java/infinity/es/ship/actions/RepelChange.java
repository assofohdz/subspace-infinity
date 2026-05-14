// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

/** Additive count delta to live {@link Repel}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code RepelCountSystem}. See ADR 0001. */
public record RepelChange(int delta) implements DeltaChange {

  public RepelChange() {
    this(0);
  }
}
