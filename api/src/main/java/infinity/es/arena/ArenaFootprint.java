// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.arena;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/** Closed-polygon footprint for the client's radar; vertices form a closed loop (last connects to first). Stamped by {@code ArenaSystem}. */
public class ArenaFootprint implements EntityComponent {

  private final Vec3d[] vertices;

  // For serialization
  public ArenaFootprint() {
    this(null);
  }

  public ArenaFootprint(final Vec3d[] vertices) {
    this.vertices = vertices;
  }

  public Vec3d[] getVertices() {
    return vertices;
  }

  /** Rectangle convenience factory; clockwise from NW: NW → NE → SE → SW. */
  public static ArenaFootprint rectangle(final Vec3d min, final Vec3d max) {
    final Vec3d[] verts = new Vec3d[] {
        new Vec3d(min.x, min.y, min.z),
        new Vec3d(max.x, min.y, min.z),
        new Vec3d(max.x, min.y, max.z),
        new Vec3d(min.x, min.y, max.z),
    };
    return new ArenaFootprint(verts);
  }
}
