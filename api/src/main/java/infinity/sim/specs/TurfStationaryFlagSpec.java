// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code MapFactory.createTurfStationaryFlag}.
 * The factory offsets the spawn by {@code (0.5, 0, 0.5)} to center the
 * flag on its tile.
 *
 * @param parent optional parent entity ({@code null} for free-standing flags)
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position pre-offset flag position in world coords
 * @param radius collision radius
 */
public record TurfStationaryFlagSpec(
    EntityId parent,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    double radius) {}
