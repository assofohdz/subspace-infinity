// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code WeaponFactory.createExplosion}. Explosions
 * are visual ghosts (no mass / collision filter) that the central decay
 * reaper deletes when {@code decayMillis} elapses.
 *
 * @param owner parent entity (typically the projectile that exploded; not
 *     stamped as {@code Parent} today but kept for symmetry with other
 *     create methods + future linking)
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position spawn position in world coords
 * @param decayMillis explosion lifetime in ms
 * @param shapeInfo pre-built shape carrying the explosion key + size
 */
public record ExplosionSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    long decayMillis,
    ShapeInfo shapeInfo) {}
