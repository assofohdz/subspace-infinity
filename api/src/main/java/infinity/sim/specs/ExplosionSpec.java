// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/** Parameter record for {@code WeaponFactory.createExplosion}; visual ghost (no mass/filter). */
public record ExplosionSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    long decayMillis,
    ShapeInfo shapeInfo) {}
