// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;

/**
 * Parameter record for {@code ShipFactory.createRocketBuff}. The buff
 * entity drives a rocket activation; lifecycle is owned by {@code Decay}.
 *
 * @param ship parent ship being buffed (revert target)
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param activeTimeMs buff lifetime in ms
 * @param originalThrust ship's {@code Thrust} value before the buff
 *     (snapshotted by the caller; restored on buff expiry)
 * @param originalSpeed ship's {@code Speed} value before the buff
 */
public record RocketBuffSpec(
    EntityId ship,
    long createdTime,
    long activeTimeMs,
    int originalThrust,
    int originalSpeed) {}
