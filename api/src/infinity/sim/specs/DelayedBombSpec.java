// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import java.util.Set;

/**
 * Parameter record for {@code WeaponFactory.createDelayedBomb}. A delayed
 * bomb adds a {@code Delay} that materializes the supplied component set
 * after {@code scheduledMillis} (e.g. a {@code GravityWell} for the
 * gravity-bomb mechanic).
 *
 * @param owner parent entity (firing ship) for {@code Parent}
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position spawn position in world coords
 * @param linearVelocity initial impulse velocity
 * @param decayMillis projectile lifetime in ms (per-arena {@code BombConfig.decayMs()})
 * @param scheduledMillis ms after creation when {@code delayedComponents} are applied
 * @param delayedComponents components to materialize on the bomb after {@code scheduledMillis}
 * @param shapeName shape key (level-suffixed bomb shape)
 * @param radius collision radius
 */
public record DelayedBombSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    Vec3d linearVelocity,
    long decayMillis,
    long scheduledMillis,
    Set<EntityComponent> delayedComponents,
    String shapeName,
    double radius) {}
