// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.opportunity;

import infinity.ai.field.ScalarField;
import java.util.Arrays;
import java.util.List;

/**
 * Value of map opportunities (prizes) over the arena tile grid (ADR-0012): each prize splats a
 * linear falloff ({@code 1 − d/radius}) so a bot's nav gradient can bend toward nearby pickups.
 * Single field per arena (prizes are team-neutral, unlike density/threat). Uniform per-prize value
 * for v1 — Subspace prize "green" value isn't differentiated yet (REFERENCE.md {@code ## Prize});
 * weight by prize type when a prize-grab behaviour needs it. Arena-relative cells; empty cells
 * read {@code 0.0}.
 */
public final class OpportunityField implements ScalarField {

  private final int width;
  private final int height;
  private final int originX;
  private final int originZ;
  private final int radius;
  private final double[] grid;

  public OpportunityField(
      final int width,
      final int height,
      final int originX,
      final int originZ,
      final int radius) {
    this.width = width;
    this.height = height;
    this.originX = originX;
    this.originZ = originZ;
    this.radius = Math.max(1, radius);
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

  /** Replace the field from this cadence's prize world-cells (each {@code int[]{worldX, worldZ}}). */
  public void rebuild(final List<int[]> prizeCells) {
    Arrays.fill(this.grid, 0.0);
    for (final int[] cell : prizeCells) {
      splatWorld(cell[0], cell[1]);
    }
  }

  private void splatWorld(final int worldCellX, final int worldCellZ) {
    final int cx = worldCellX - this.originX;
    final int cz = worldCellZ - this.originZ;
    final int r = this.radius;
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
        this.grid[z * this.width + x] += 1.0 - d / r;
      }
    }
  }
}
