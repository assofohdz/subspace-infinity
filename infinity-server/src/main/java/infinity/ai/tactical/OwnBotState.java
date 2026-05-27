// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

/**
 * Per-cycle snapshot of the bot's own ECS state that the ADR-0016 input vocabulary needs but the
 * {@link infinity.ai.brain.Blackboard} doesn't already carry — sampled by {@code BotBrainSystem}
 * (which has {@code EntityData}) and handed to {@link SituationalInputsFactory}. Extend with new
 * own-state fields as behaviours need them (e.g. item charges) rather than growing the factory
 * signature. {@code weaponReady} feeds {@code recharge_rdy}; {@code concealed} feeds {@code concealment}.
 */
public record OwnBotState(boolean weaponReady, boolean concealed) {}
