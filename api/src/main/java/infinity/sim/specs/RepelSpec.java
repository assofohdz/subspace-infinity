// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code WeaponFactory.createRepel}.
 *
 * @param owner parent entity (firing ship) for {@code Parent}
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position spawn position in world coords
 * @param decayMillis effect lifetime in ms (per-arena {@code RepelConfig.timeMs()})
 * @param radius collision radius
 */
public record RepelSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    long decayMillis,
    double radius) {}
