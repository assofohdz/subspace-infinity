// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code WeaponFactory.createBomb}.
 *
 * @param owner parent entity (firing ship) for {@code Parent}
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position spawn position in world coords
 * @param linearVelocity initial impulse velocity
 * @param decayMillis projectile lifetime in ms (per-arena {@code BombConfig.decayMs()})
 * @param shapeName shape key (level-suffixed bomb shape)
 * @param radius collision radius
 */
public record BombSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    Vec3d linearVelocity,
    long decayMillis,
    String shapeName,
    double radius) {}
