// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.es.EntityId;

/**
 * Per-bot perception query — yields nearby ships (filtered by team via {@link NearbyShip#frequency})
 * and obstacles (dynamic bodies + forward-sampled solid world cells) within {@code radius}.
 * Server-side implementation wraps {@code mphys.BinIndex} + {@code mworld.World}.
 */
public interface Perception {

  PerceptionSnapshot perceive(EntityId bot, MoverState self, double radius);
}
