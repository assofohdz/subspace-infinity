// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import java.util.List;

/**
 * Per-tick view of nearby world surface a bot brain consumes. Server-only transient;
 * never wire-crossing.
 */
public record PerceptionSnapshot(
    List<NearbyShip> threats, List<NearbyShip> allies, List<NearbyObstacle> obstacles) {

  public static final PerceptionSnapshot EMPTY =
      new PerceptionSnapshot(List.of(), List.of(), List.of());
}
