// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code MapFactory.createWarpEffect}. A warp effect
 * is a visual ghost (no mass / collision filter) that the central decay
 * reaper deletes when {@code decayMillis} elapses.
 *
 * @param parent optional parent entity ({@code null} for unparented
 *     effects); the WARP shape uses zero radius regardless
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position effect position in world coords
 * @param decayMillis effect lifetime in ms
 */
public record WarpEffectSpec(
    EntityId parent,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    long decayMillis) {}
