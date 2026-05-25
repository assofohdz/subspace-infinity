// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import com.simsilica.es.EntityId;

/** Break off and flee {@code threat} (low-energy / outmatched); invalid once the threat despawns. See ADR-0013. */
public record Disengage(EntityId threat) implements TacticalGoal {}
