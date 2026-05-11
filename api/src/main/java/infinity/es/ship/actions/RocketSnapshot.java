// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Pre-buff snapshot of Thrust/Speed stamped on the rocket-buff entity. Passive marker (additive-delta model handles revert); kept for forward-compat / debugging. */
public class RocketSnapshot implements EntityComponent {

  private final int originalThrust;
  private final int originalSpeed;

  public RocketSnapshot() {
    this(0, 0);
  }

  public RocketSnapshot(final int originalThrust, final int originalSpeed) {
    this.originalThrust = originalThrust;
    this.originalSpeed = originalSpeed;
  }

  public int getOriginalThrust() {
    return originalThrust;
  }

  public int getOriginalSpeed() {
    return originalSpeed;
  }

  @Override
  public String toString() {
    return "RocketSnapshot[thrust=" + originalThrust + ", speed=" + originalSpeed + "]";
  }
}
