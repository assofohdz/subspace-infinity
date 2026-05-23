// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.PerceptionSnapshot;

/**
 * Composite that delegates to children in priority order: first non-null result wins.
 * Returns {@code null} when every child has no opinion this tick. Caller composes the
 * fallback (e.g. an idle wander) as the final delegate. See ADR-0009.
 */
public final class PrioritySteering implements Steering {

  private final Steering[] delegates;

  public PrioritySteering(final Steering... delegates) {
    this.delegates = delegates.clone();
  }

  @Override
  public Vec3d steer(final MoverState self, final PerceptionSnapshot perception) {
    for (final Steering delegate : this.delegates) {
      final Vec3d result = delegate.steer(self, perception);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
