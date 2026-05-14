// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import infinity.es.ship.actions.InventoryCount;

/** Live gravbomb inventory count; clamped at {@link GravBombStats#max()} by {@code GravBombSystem}. See ADR 0001. */
public class GravBomb implements InventoryCount {

  private final int count;

  public GravBomb() {
    this(0);
  }

  public GravBomb(final int count) {
    this.count = count;
  }

  public int getCount() {
    return count;
  }

  @Override
  public int count() {
    return count;
  }
}
