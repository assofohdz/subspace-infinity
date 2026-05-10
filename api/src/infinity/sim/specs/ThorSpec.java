// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code WeaponFactory.createThor}.
 *
 * <p>{@code attackVelocity} is preserved in the call signature for
 * symmetry with other projectile factories even though the thor factory
 * does not currently apply it as an Impulse — the canonical Subspace
 * mechanic uses different motion semantics.
 *
 * @param owner parent entity (firing ship) for {@code Parent}
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position spawn position in world coords
 * @param attackVelocity unused today; reserved for future linking
 * @param decayMillis projectile lifetime in ms (per-arena {@code ThorConfig.decayMs()})
 * @param radius collision radius
 */
public record ThorSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    Vec3d attackVelocity,
    long decayMillis,
    double radius) {}
