// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Repulsion speed on a Repel effect entity ({@code [Repel] RepelSpeed}); stamped at fire-time from {@link infinity.config.RepelConfig}. */
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
