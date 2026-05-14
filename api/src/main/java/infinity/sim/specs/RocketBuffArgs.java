// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;

/** Parameter record for {@code ShipFactory.createRocketBuff}; {@code originalThrust}/{@code originalSpeed} captured for revert on buff expiry. */
public record RocketBuffArgs(
    EntityId ship,
    long createdTime,
    long activeTimeMs,
    int originalThrust,
    int originalSpeed) {}
