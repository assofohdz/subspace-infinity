// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Additive delta (rad/sec) to live {@link Rotation}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code RotationSystem}. See ADR 0001. */
public record RotationChange(double delta) implements EntityComponent {

  public RotationChange() {
    this(0.0);
  }
}
