// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Repulsion speed of a Repel effect entity. Stamped at fire-time by
 * {@code ConsumableSystem} from per-arena {@link infinity.config.RepelConfig}.
 * Carries the Subspace {@code [Repel] RepelSpeed} value (raw integer in
 * Subspace velocity units) per the Pattern 4 template → spawn-projection →
 * component flow; future repel-impulse system reads this on the spawned
 * entity rather than the {@code RepelConfig} template.
 *
 * @author Asser
 */
public class RepelSpeed implements EntityComponent {

  private final int speed;

  public RepelSpeed() {
    this(0);
  }

  public RepelSpeed(final int speed) {
    this.speed = speed;
  }

  public int getSpeed() {
    return speed;
  }
}
