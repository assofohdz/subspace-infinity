// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena brick tuning ({@code BrickSpan} / {@code BrickTime} cs×10); see REFERENCE.md {@code ## Brick}. */
public record BrickConfig(int spanTiles, long timeMs) {

  /** Subspace-canonical baseline ({@code BrickSpan 7}, {@code BrickTime 120000ms}). */
  public static final BrickConfig DEFAULTS = new BrickConfig(7, 120_000L);
}
