// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for the explicit-radius {@code MapFactory.createOver5}.
 * OVER5 is a sized animation overlay (no gravity, no warp behavior).
 *
 * @param owner currently unused by the factory; preserved for future
 *     parent-linkage symmetry
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position overlay position in world coords
 * @param radius collision radius
 */
public record Over5Spec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    double radius) {}
