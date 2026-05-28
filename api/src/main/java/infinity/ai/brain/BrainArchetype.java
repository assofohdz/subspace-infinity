// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.Behavior;
import infinity.config.BotBrainConfig;
import infinity.config.ZoneBotAiConfig;

/**
 * Factory for a named brain archetype. {@code createRoot} + {@code createBlackboard} take
 * both a per-arena {@link BotBrainConfig} (perception/orbit/wander knobs) and a zone-tier
 * {@link ZoneBotAiConfig} (steer-action engine constants like {@code steerGoalBlockCells},
 * {@code seekForwardThrustFloor}, {@code wallRepulsionMinPush}) so all tunable values flow
 * into the BT leaves + steering primitives at spawn. See ADR-0009 / ADR-0010.
 */
public interface BrainArchetype {
  String name();

  Behavior createRoot(BotBrainConfig config, ZoneBotAiConfig zoneCfg);

  Blackboard createBlackboard(BotBrainConfig config, ZoneBotAiConfig zoneCfg);
}
