// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/** Parameter record for {@code MapFactory.createDoor}; {@code owner} may be {@code null} for free-standing doors. */
public record DoorSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    long intervalTime,
    Vec3d position) {}
