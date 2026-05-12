// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/** Arm-time stamp on a {@link ProximityFuse}-bearing projectile; consumed by {@code ProximityFuseSystem} against {@link ProximityFuse#getFuseMs()}. */
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
