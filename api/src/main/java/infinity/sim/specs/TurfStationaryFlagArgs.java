// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/** Parameter record for {@code MapFactory.createTurfStationaryFlag}; factory offsets by {@code (0.5,0,0.5)} to tile-center. */
public record TurfStationaryFlagArgs(
    EntityId parent,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    double radius) {}
