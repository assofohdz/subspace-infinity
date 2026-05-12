// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/** Parameter record for {@code MapFactory.createWormhole}; gravity well + touch sensor pair warping ships to {@code warpTargetLocation}. */
public record WormholeSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    double force,
    String gravityType,
    Vec3d warpTargetLocation,
    double scale) {}
