// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/** Parameter record for {@code MapFactory.createPrize}; non-positive {@code decayMillis} clamps to {@link infinity.sim.MapFactory#PRIZE_DEFAULT_DECAY_MS}. */
public record PrizeSpec(
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    String prizeType,
    long decayMillis,
    boolean hidden,
    double radius) {}
