// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Effect radius of a Repel effect entity, in Subspace pixels. Stamped at
 * fire-time by {@code ConsumableSystem} from per-arena
 * {@link infinity.config.RepelConfig}. Carries the Subspace
 * {@code [Repel] RepelDistance} value per the Pattern 4 template →
 * spawn-projection → component flow; future repel-impulse system reads
 * this on the spawned entity rather than the {@code RepelConfig}
 * template.
 *
 * @author Asser
 */
public class RepelDistance implements EntityComponent {

  private final int pixels;

  public RepelDistance() {
    this(0);
  }

  public RepelDistance(final int pixels) {
    this.pixels = pixels;
  }

  public int getPixels() {
    return pixels;
  }
}
