// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.bt;

import infinity.ai.brain.Blackboard;

/** Behaviour Tree node. Composites + leaves both extend this; see ADR-0009. */
public interface Behavior {
  Status tick(Blackboard blackboard);
}
