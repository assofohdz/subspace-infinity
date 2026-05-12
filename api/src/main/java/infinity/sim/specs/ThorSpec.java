// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/** Parameter record for {@code WeaponFactory.createThor}; {@code attackVelocity} currently unused. */
public record ThorSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    Vec3d attackVelocity,
    long decayMillis,
    double radius) {}
