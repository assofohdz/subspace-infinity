// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

import infinity.Ship;
import java.util.List;

/**
 * One entry in a {@code bots { }} block (ADR-0010 as amended by ADR-0014): spawn {@code count} bots
 * of {@code ship}, with an optional per-ship {@code tweak} overlay on their capability-derived
 * behaviour weights. No {@code weights} / {@code archetype} field — weights are derived (slice #04),
 * not authored.
 */
public record BotShipConfig(Ship ship, int count, List<BehaviourTweak> tweak) {

  public BotShipConfig {
    if (count < 0) {
      throw new IllegalArgumentException("bot ship count must be >= 0; got " + count);
    }
    tweak = List.copyOf(tweak);
  }

  public BotShipConfig(final Ship ship, final int count) {
    this(ship, count, List.of());
  }
}
