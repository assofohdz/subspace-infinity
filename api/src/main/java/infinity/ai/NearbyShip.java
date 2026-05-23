// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

/**
 * Snapshot of a ship the perception layer surfaces to a bot brain. {@code frequency}
 * carries the freq value at sample time so brains can classify ally vs threat without
 * re-querying ECS.
 */
public record NearbyShip(
    EntityId id, Vec3d position, Quatd orientation, Vec3d velocity, int frequency) {}
