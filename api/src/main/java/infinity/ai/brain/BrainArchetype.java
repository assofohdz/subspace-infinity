// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.Behavior;

/**
 * Factory for a named brain archetype. {@link #createRoot()} may return a shared
 * stateless BT — per-bot state lives in {@link #createBlackboard()}. See ADR-0009.
 */
public interface BrainArchetype {
  String name();

  Behavior createRoot();

  Blackboard createBlackboard();
}
