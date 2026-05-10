// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Marks a ship as currently being screen-jittered from a recent bomb hit.
 * The deadline lives on the component; a small reaper system removes the
 * component when {@code now &gt;= endTime}.
 *
 * <p>Stamped server-side by {@code WeaponsSystem} after a bomb's damage path
 * passes the friendly-fire gate (Q1 of slice 9c-JitterTime: jitter follows
 * damage, never decoupled). Read client-side by {@code JitterState} on the
 * local avatar id to drive a decaying-amplitude camera-offset perturbation.
 *
 * <p>Crosses the wire (server writes, client {@code watchEntity} reads) — must
 * be registered in {@code GameServer.registerSerializers()} per
 * {@code .claude/rules/components.md}.
 *
 * <p>Shape mirrors {@code com.simsilica.es.common.Decay}: two longs in nanos,
 * {@code (startTime, endTime)}. Client computes binary "still shaking?" via
 * {@code now &lt; endTime} and amplitude-decay progress via
 * {@code (now - startTime) / (endTime - startTime)}.
 *
 * <p><b>Overlap (slice Q4=a):</b> when a second bomb hit lands during an
 * existing shake, the stamp helper takes {@code max(existing.endTime,
 * newEnd)} and resets {@code startTime = now} only when the new hit genuinely
 * extends the deadline. A weaker hit does not shorten an in-flight shake.
 *
 * <p><b>Subspace canon (REFERENCE.md ## Bomb):</b> {@code JitterTime — Screen
 * jitter duration on bomb hit (1/100s)}. Authored in centiseconds; converted
 * to milliseconds at the {@code BombAdapter} loader boundary, then to nanos
 * at stamp time.
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
