// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.arena;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/**
 * Presentation marker that tells the client's radar an entity has a closed
 * polygon footprint region — vs the per-entity-point blip pattern carried by
 * {@link RadarShapeInfo} (which names a blip shape: "dot", "square",
 * "diamond" etc).
 *
 * <p>Server publishes the geometry; the client decides the styling — fill
 * tint, outline color, line thickness all come from {@code RadarTheme}, not
 * from this component. Per the api-contracts.md / client-read-only.md split:
 * server owns truth (where the polygon is), client owns presentation (how it
 * looks).
 *
 * <p>Vertices form a closed polygon — last vertex connects implicitly back to
 * the first. For convex polygons (e.g., arena bounds rectangles) the radar
 * fan-triangulates from {@code vertices[0]} for the interior fill and renders
 * a {@code Mesh.Mode.Lines} loop for the outline.
 *
 * <p>Today only arenas carry this component (see {@code ArenaSystem}). The
 * shape is generic so future entity types — wormholes, safe zones, capture
 * footprints, eLVL regions — can stamp themselves with a {@code ArenaFootprint}
 * and inherit the same render path.
 */
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

  /**
   * Convenience factory for the rectangle case — the dominant shape today
   * (arena bounds). Returns a 4-vertex polygon traversed clockwise from the
   * NW corner: NW → NE → SE → SW.
   */
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
