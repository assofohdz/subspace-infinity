// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Marks a {@link ProximityFuse}-bearing projectile as armed — an enemy has
 * entered the projectile's proximity radius and the fuse is now counting
 * down. {@code ProximityFuseSystem} consults {@link #getArmedAtNanos()} +
 * {@code ProximityFuse.getFuseMs()} to decide when to detonate.
 *
 * <p>Server-only — stamped and consumed by
 * {@code infinity.systems.ProximityFuseSystem}; never read by client
 * code, so no {@code Serializer.registerClass} entry is required (per
 * {@code .claude/rules/components.md}).
 *
 * <p>The timestamp is captured from {@code SimTime.getTime()} (simulation
 * nanos) at arm time, matching the clock {@code Decay} uses, so fuse
 * deadline arithmetic stays in the same time domain as the rest of the
 * projectile lifecycle.
 */
public final class ProximityArmed implements EntityComponent {

  private final long armedAtSimNanos;

  public ProximityArmed() {
    this(0L);
  }

  public ProximityArmed(final long armedAtSimNanos) {
    this.armedAtSimNanos = armedAtSimNanos;
  }

  /** Simulation timestamp ({@code SimTime.getTime()} nanos) when arming fired. */
  public long getArmedAtSimNanos() {
    return armedAtSimNanos;
  }
}
