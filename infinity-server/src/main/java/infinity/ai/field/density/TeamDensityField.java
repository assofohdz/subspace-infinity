// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.density;

import infinity.ai.field.ScalarField;
import java.util.Arrays;

/**
 * Per-team ship-position density over the arena tile grid (ADR-0012) — each ship on the team splats
 * a linear-falloff kernel ({@code 1 − d/radius}) into nearby cells, so {@link infinity.ai.field.FieldGradient}
 * yields a smooth "toward where this team clusters" direction. Arena-relative cells (the grid is
 * indexed the same way as the navigation passability grid); out-of-range / empty cells read
 * {@code 0.0} (no signal — not {@code +∞}, which is the distance-field's unreachable sentinel).
 *
 * <p>Rebuilt in place each density cadence by {@link ArenaDensity}; {@link infinity.ai.field.FieldBlend}
 * may hold a stable reference across rebuilds.
 */
public final class TeamDensityField implements ScalarField {

  private final int width;
  private final int height;
  private final int originX;
  private final int originZ;
  private final int kernelRadius;
  private final double[] grid;

  TeamDensityField(
      final int width,
      final int height,
      final int originX,
      final int originZ,
      final int kernelRadius) {
    this.width = width;
    this.height = height;
    this.originX = originX;
    this.originZ = originZ;
    this.kernelRadius = Math.max(1, kernelRadius);
    this.grid = new double[Math.max(0, width * height)];
  }

  @Override
  public int width() {
    return this.width;
  }

  @Override
  public int height() {
    return this.height;
  }

  @Override
  public double valueAt(final int x, final int y) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
      return 0.0;
    }
    return this.grid[y * this.width + x];
  }

  void clear() {
    Arrays.fill(this.grid, 0.0);
  }

  /** Add one ship at an absolute world cell, splatting a linear-falloff kernel into the relative grid. */
  void splatWorld(final int worldCellX, final int worldCellZ, final double weight) {
    final int cx = worldCellX - this.originX;
    final int cz = worldCellZ - this.originZ;
    final int r = this.kernelRadius;
    for (int dz = -r; dz <= r; dz++) {
      final int z = cz + dz;
      if (z < 0 || z >= this.height) {
        continue;
      }
      for (int dx = -r; dx <= r; dx++) {
        final int x = cx + dx;
        if (x < 0 || x >= this.width) {
          continue;
        }
        final double d = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (d > r) {
          continue;
        }
        this.grid[z * this.width + x] += weight * (1.0 - d / r);
      }
    }
  }
}
