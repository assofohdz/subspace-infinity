// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

/**
 * Snapshot of a ship the perception layer surfaces to a bot brain. {@code frequency}
 * carries the freq value at sample time so brains can classify ally vs threat without
 * re-querying ECS. {@code energyPct} is the ship's energy as a {@code [0,1]} fraction of max
 * ({@code -1} when not sampled); {@code bounty} is its kill-worth — both feed the ADR-0016
 * situational-fit inputs ({@code energy_adv}, {@code bounty_pull}).
 */
public record NearbyShip(
    EntityId id,
    Vec3d position,
    Quatd orientation,
    Vec3d velocity,
    int frequency,
    double energyPct,
    int bounty) {

  /** Back-compat 5-arg form (steering tests, non-perception callers); energy/bounty unsampled. */
  public NearbyShip(
      final EntityId id,
      final Vec3d position,
      final Quatd orientation,
      final Vec3d velocity,
      final int frequency) {
    this(id, position, orientation, velocity, frequency, -1.0, 0);
  }
}
