// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.threat;

import infinity.ai.field.NavGrids;
import infinity.ai.field.ScalarField;
import java.util.Arrays;

/**
 * Danger posed by one team's ships over the arena tile grid (ADR-0012): each ship contributes a
 * linear weapon-range falloff ({@code 1 − d/radius}) to cells it can <em>see</em> — a wall between
 * the cell and the ship zeroes the contribution, so "behind cover" reads safe. A bot reads the
 * blend of enemy freqs for its incoming-threat surface and bends its nav gradient away from it.
 *
 * <p>Weapon range is approximated by a single {@code threatRadius} knob rather than per-ship,
 * per-weapon Subspace ranges (REFERENCE.md {@code ## Bullet} / {@code ## Bomb}); a richer model can
 * replace the uniform radius when per-weapon evade tuning demands it. Arena-relative cells; empty /
 * out-of-range cells read {@code 0.0}.
 */
public final class ThreatField implements ScalarField {

  private final int width;
  private final int height;
  private final int originX;
  private final int originZ;
  private final int radius;
  private final boolean[][] passable;
  private final double[] grid;

  ThreatField(
      final int width,
      final int height,
      final int originX,
      final int originZ,
      final int radius,
      final boolean[][] passable) {
    this.width = width;
    this.height = height;
    this.originX = originX;
    this.originZ = originZ;
    this.radius = Math.max(1, radius);
    this.passable = passable;
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

  /** Add one ship at an absolute world cell, splatting weapon-range falloff into cells it can see. */
  void splatWorld(final int worldCellX, final int worldCellZ, final double weight) {
    final int sx = worldCellX - this.originX;
    final int sz = worldCellZ - this.originZ;
    final int r = this.radius;
    for (int dz = -r; dz <= r; dz++) {
      final int z = sz + dz;
      if (z < 0 || z >= this.height) {
        continue;
      }
      for (int dx = -r; dx <= r; dx++) {
        final int x = sx + dx;
        if (x < 0 || x >= this.width) {
          continue;
        }
        final double d = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (d > r || !NavGrids.lineOfSight(this.passable, x, z, sx, sz)) {
          continue;
        }
        this.grid[z * this.width + x] += weight * (1.0 - d / r);
      }
    }
  }
}
