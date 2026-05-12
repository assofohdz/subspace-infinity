// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/** Parameter record for {@code ShipFactory.createShip} / {@code createPlayerShip}. */
public record ShipSpec(
    Vec3d spawnLoc,
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    byte ship,
    double radius) {}
