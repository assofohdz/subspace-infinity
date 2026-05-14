// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import infinity.es.DeltaChange;

/** Additive ordinal delta to live {@link BombCurrentLevel}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code BombSystem}. See ADR 0001. */
public record BombChange(int delta) implements DeltaChange {

  public BombChange() {
    this(0);
  }
}
