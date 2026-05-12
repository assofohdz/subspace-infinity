// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

/** Camera-facing quad helpers (xz-plane, normals +Y for top-down lighting) shared by sprite factories. */
final class QuadMeshes {

  private QuadMeshes() {
    throw new AssertionError("no instances");
  }

  static float[] verticesQuad(final float halfSize) {
    return new float[] {
       halfSize, 0, -halfSize,
      -halfSize, 0, -halfSize,
      -halfSize, 0,  halfSize,
       halfSize, 0,  halfSize
    };
  }

  static float[] normalsQuad() {
    return new float[] {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0};
  }
}
