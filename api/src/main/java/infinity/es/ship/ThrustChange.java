// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Additive delta to live {@link Thrust}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code ThrustSystem}. With {@code Decay}: temporary override (rocket buff). See ADR 0001. */
public record ThrustChange(int delta) implements EntityComponent {

  public ThrustChange() {
    this(0);
  }
}
