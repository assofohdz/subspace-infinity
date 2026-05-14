// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import infinity.es.DeltaChange;

/** Additive count delta to live {@link GravBomb}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code GravBombSystem}. See ADR 0001. */
public record GravBombChange(int delta) implements DeltaChange {

  public GravBombChange() {
    this(0);
  }
}
