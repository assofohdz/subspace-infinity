// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/** Intent payload requesting a death-drop prize at {@code position}; pairs with {@link ChangeTarget} (target = dying ship). One-shot. Drained by {@code DeathPrizeSystem}. See ADR 0001. */
public record PrizeSpawnIntent(Vec3d position, long timeNs) implements EntityComponent {

  public PrizeSpawnIntent() {
    this(null, 0L);
  }
}
