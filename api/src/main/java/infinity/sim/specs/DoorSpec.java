// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code MapFactory.createDoor}. A door cycles open
 * / closed on {@code intervalTime} cadence; ownership ({@code owner})
 * may be {@code null} for free-standing doors.
 *
 * @param owner optional parent entity ({@code null} = free-standing door)
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param intervalTime door open/close interval in ms
 * @param position door position in world coords
 */
public record DoorSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    long intervalTime,
    Vec3d position) {}
