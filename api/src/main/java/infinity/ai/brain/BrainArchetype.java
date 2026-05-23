// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.Behavior;
import infinity.config.BotBrainConfig;

/**
 * Factory for a named brain archetype. {@code createRoot} + {@code createBlackboard}
 * both take a {@link BotBrainConfig} so per-arena Groovy tunables flow into the BT
 * leaves and steering primitives at spawn. See ADR-0009 / ADR-0010.
 */
public interface BrainArchetype {
  String name();

  Behavior createRoot(BotBrainConfig config);

  Blackboard createBlackboard(BotBrainConfig config);
}
