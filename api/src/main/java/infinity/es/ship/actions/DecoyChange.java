// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Additive count delta to live {@link Decoy}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code DecoySystem}. See ADR 0001. */
public record DecoyChange(int delta) implements EntityComponent {

  public DecoyChange() {
    this(0);
  }
}
