// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import infinity.es.DeltaChange;

/** Additive count delta to live {@link Brick}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code BrickSystem}. See ADR 0001. */
public record BrickChange(int delta) implements DeltaChange {

  public BrickChange() {
    this(0);
  }
}
