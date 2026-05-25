// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import com.simsilica.es.EntityId;

/** Close on {@code target} and fire when aimed; invalid once the target despawns. See ADR-0013. */
public record Engage(EntityId target) implements TacticalGoal {}
