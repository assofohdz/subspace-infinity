// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code MapFactory.createWormhole}. The wormhole is
 * a static gravity well paired with a touch sensor entity that warps any
 * ship that contacts it to {@code warpTargetLocation}.
 *
 * @param owner currently unused by the factory; preserved for future
 *     parent-linkage symmetry
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position wormhole position in world coords
 * @param force gravity-well pull strength
 * @param gravityType gravity-well kind (e.g. {@code GravityWell.PULL})
 * @param warpTargetLocation destination position for ships that touch the wormhole
 * @param scale shape size (drives the wormhole + warp-sensor radii)
 */
public record WormholeSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    double force,
    String gravityType,
    Vec3d warpTargetLocation,
    double scale) {}
