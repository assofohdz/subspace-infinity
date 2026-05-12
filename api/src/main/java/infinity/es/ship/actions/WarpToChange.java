// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/** Value-replacement payload requesting a warp to {@code target}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code WarpSystem}. One-shot only. See ADR 0001. */
public record WarpToChange(Vec3d target) implements EntityComponent {

  public WarpToChange() {
    this(null);
  }
}
