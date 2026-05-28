// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

/** Kinematic snapshot of a steering subject for one tick. Server-only transient. */
public record MoverSnapshot(Vec3d position, Quatd orientation, Vec3d velocity) {}
