// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code WeaponFactory.createMine}.
 *
 * @param owner parent entity (placing ship) for {@code Parent}
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position spawn position in world coords
 * @param decayMillis mine lifetime in ms (per-arena {@code MineConfig.decayMs()})
 * @param shapeName shape key (level-suffixed mine shape)
 * @param radius collision radius
 */
public record MineSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    long decayMillis,
    String shapeName,
    double radius) {}
