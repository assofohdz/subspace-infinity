// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import java.util.Set;

/** Parameter record for {@code WeaponFactory.createDelayedBomb}; {@code delayedComponents} materialize after {@code scheduledMillis} (e.g. {@code GravityWell} for gravbomb). */
public record DelayedBombArgs(
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
