// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.mathd.Vec3d;

/**
 * Spherical obstacle the perception layer surfaces to {@link infinity.ai.steer.AvoidObstacles}.
 * Source-agnostic: dynamic body (asteroid, other ship) or synthetic from a sampled solid
 * world cell — both flow through the same Reynolds corridor projection.
 */
public record NearbyObstacle(Vec3d position, double radius) {}
