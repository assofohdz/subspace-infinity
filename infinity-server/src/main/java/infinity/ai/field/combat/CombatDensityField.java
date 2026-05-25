// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.combat;

import infinity.ai.field.ScalarField;

/**
 * "Where the fighting is" over the arena tile grid (ADR-0012): each weapon fire splats a linear
 * falloff at its origin, and the whole field is multiplicatively time-decayed each cadence so heat
 * fades when fire stops. Unlike the position-density fields (full rebuild per cadence), this one is
 * <em>incremental</em> — decay then add new shots — so it remembers recent combat. Single field per
 * arena (team-neutral). Combined with chokepoint geometry to rank tactically-hot pinch points.
 * Arena-relative cells; empty cells read {@code 0.0}.
 */
public final class CombatDensityField implements ScalarField {

  private final int width;
  private final int height;
  private final int originX;
  private final int originZ;
  private final int radius;
  private final double decay;
  private final double[] grid;

  public CombatDensityField(
      final int width,
      final int height,
      final int originX,
      final int originZ,
      final int radius,
      final double decayPerCadence) {
    this.width = width;
    this.height = height;
    this.originX = originX;
    this.originZ = originZ;
    this.radius = Math.max(1, radius);
    this.decay = decayPerCadence;
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

  /** Fade existing heat one cadence step — call once before splatting this cadence's new shots. */
  public void decay() {
    for (int i = 0; i < this.grid.length; i++) {
      this.grid[i] *= this.decay;
    }
  }

  /** Add one fire event at an absolute world cell, splatting a linear falloff into the relative grid. */
  public void addShot(final int worldCellX, final int worldCellZ) {
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
