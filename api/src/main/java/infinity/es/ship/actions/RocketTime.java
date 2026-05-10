// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship rocket buff lifetime (Subspace {@code [Ship] RocketTime}).
 *
 * <p>Projected onto the ship at spawn from
 * {@link infinity.config.RocketStats#activeTimeCs}. Read at fire-time
 * by {@code ConsumableSystem} to compute the buff entity's
 * {@link com.simsilica.es.common.Decay} deadline.
 *
 * <p>Held in milliseconds (the conversion from Subspace centiseconds
 * happens at the loader/projection boundary, not here).
 */
public class RocketTime implements EntityComponent {

  private final long activeTimeMs;

  public RocketTime() {
    this(0L);
  }

  public RocketTime(final long activeTimeMs) {
    this.activeTimeMs = activeTimeMs;
  }

  public long getActiveTimeMs() {
    return activeTimeMs;
  }

  @Override
  public String toString() {
    return "RocketTime[" + activeTimeMs + " ms]";
  }
}
