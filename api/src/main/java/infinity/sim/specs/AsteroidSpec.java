// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for the explicit-radius asteroid factories ({@code
 * MapFactory.createAsteroidSmall} and {@code createAsteroidMedium}). The
 * two methods share an input shape; only the projected {@code ShapeNames}
 * key differs (OVER1 vs OVER2).
 *
 * @param owner currently unused by the factory; preserved for future
 *     parent-linkage symmetry
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position asteroid position in world coords
 * @param mass asteroid mass (0 = static)
 * @param radius collision radius
 */
public record AsteroidSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    double mass,
    double radius) {}
