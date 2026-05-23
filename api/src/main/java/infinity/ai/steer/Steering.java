// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.PerceptionSnapshot;

/**
 * Pure-math steering primitive. Returns a rate-shaped intent
 * {@code Vec3d(turnRate, _, thrust)} in {@code [-1, 1]} (same shape as keyboard analog
 * input — maps directly to {@code MovementInput.move}), or {@code null} when the primitive
 * has no opinion this tick (lets {@code PrioritySteering} fall through to the next
 * delegate). See ADR-0009.
 */
public interface Steering {

  Vec3d steer(MoverState self, PerceptionSnapshot perception);
}
