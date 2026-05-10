// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code WeaponFactory.createBurst}.
 *
 * <p>{@code linearVelocity} is preserved in the call signature for
 * symmetry with other projectile factories even though the burst factory
 * does not currently apply it (Subspace bursts have a fixed-radius
 * semantic, not an Impulse).
 *
 * @param owner parent entity (firing ship) for {@code Parent}
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position spawn position in world coords
 * @param linearVelocity unused today; reserved for future per-shrap velocity
 * @param decayMillis projectile lifetime in ms (per-arena {@code BurstConfig.decayMs()})
 * @param radius collision radius
 */
public record BurstSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    Vec3d linearVelocity,
    long decayMillis,
    double radius) {}
