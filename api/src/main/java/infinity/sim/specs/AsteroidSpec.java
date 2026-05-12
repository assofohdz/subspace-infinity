// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/** Parameter record for {@code MapFactory.createAsteroidSmall} / {@code createAsteroidMedium} (OVER1 vs OVER2). */
public record AsteroidSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    double mass,
    double radius) {}
