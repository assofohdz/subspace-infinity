// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Screen-jitter window on a ship after a bomb hit; {@code (startTime,endTime)} in nanos, mirrors {@code Decay} shape. See REFERENCE.md {@code JitterTime}.
 *
 * <p>Subspace canon authors {@code JitterTime} in centiseconds; {@code BombAdapter} converts ×10 to ms,
 * then to nanos at stamp time. Overlapping hits take {@code max(existing.endTime, newEnd)} — a weaker hit
 * never shortens an in-flight shake.
 */
public final class Jitter implements EntityComponent {

  private final long startTime;
  private final long endTime;

  public Jitter() {
    this(0L, 0L);
  }

  public Jitter(final long startTime, final long endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
  }

  /** Shake start time in nanoseconds (server {@code SimTime.getTime()} units). */
  public long getStartTime() {
    return startTime;
  }

  /** Shake end time in nanoseconds (server {@code SimTime.getTime()} units). */
  public long getEndTime() {
    return endTime;
  }

  @Override
  public String toString() {
    return "Jitter[start=" + startTime + ",end=" + endTime + "]";
  }
}
