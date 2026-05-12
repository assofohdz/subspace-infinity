// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/** Proximity-fuse marker on projectiles ({@code ProximityDistance} / {@code BombExplodeDelay} from REFERENCE.md {@code ## Bomb}); scanned by {@code ProximityFuseSystem}. */
public final class ProximityFuse implements EntityComponent {

  private final double radiusWorldUnits;
  private final long fuseMs;

  public ProximityFuse() {
    this(0.0, 0L);
  }

  public ProximityFuse(final double radiusWorldUnits, final long fuseMs) {
    this.radiusWorldUnits = radiusWorldUnits;
    this.fuseMs = fuseMs;
  }

  /** Proximity-arm radius in world units (1 unit ≈ 1 tile). */
  public double getRadiusWorldUnits() {
    return radiusWorldUnits;
  }

  /** Fuse delay in milliseconds between arming and detonation. */
  public long getFuseMs() {
    return fuseMs;
  }
}
