// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

/**
 * Package-private helpers shared by {@link SISpatialFactory} and
 * {@link EffectSpatialFactory} for building the camera-facing quads used by
 * sprite-based gameplay-entity spatials (ship/flag/bullet/bomb/bounty) and
 * effect spatials (explosions, over-layers, warp/repel/burst, wormhole).
 *
 * <p>The vertex layout is xz-plane facing +Y so the top-down camera sees the
 * sprite. The normals point along +Y for lighting.
 */
final class QuadMeshes {

  private QuadMeshes() {
    throw new AssertionError("no instances");
  }

  /**
   * Quad bounds in xz, ordered for the top-down camera. {@code halfSize} is
   * the quad's half-edge in world units.
   */
  static float[] verticesQuad(final float halfSize) {
    return new float[] {
       halfSize, 0, -halfSize,
      -halfSize, 0, -halfSize,
      -halfSize, 0,  halfSize,
       halfSize, 0,  halfSize
    };
  }

  /**
   * Per-vertex normals pointing along +Y so the lit material reads "facing
   * the camera" for a top-down view.
   */
  static float[] normalsQuad() {
    return new float[] {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0};
  }
}
