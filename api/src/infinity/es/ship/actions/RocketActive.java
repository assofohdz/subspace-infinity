// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Marker component installed on a ship while a rocket buff is active.
 * Maintained by {@code RocketBuffSystem} as a denormalized cache of
 * "ship has a child rocket-buff entity right now" — hot-path consumers
 * (HUD, AI, anything that wants to gate on rocket-active state) can
 * watch this single component instead of joining the ship to its
 * children.
 *
 * <p>Source of truth for the buff's lifetime is the buff entity's
 * {@link com.simsilica.es.common.Decay}; this marker is added on buff
 * creation and removed when the buff entity expires.
 */
public class RocketActive implements EntityComponent {

  public RocketActive() {}

  @Override
  public String toString() {
    return "RocketActive[]";
  }
}
