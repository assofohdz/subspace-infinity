// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena Repel tuning ({@code RepelSpeed} / {@code RepelTime} cs×10 / {@code RepelDistance} px÷16); see REFERENCE.md {@code ## Repel}. {@code distanceTiles} authored in tiles, not Subspace pixels. */
public record RepelConfig(int speed, long timeMs, double distanceTiles) {

  /** Subspace-canonical baseline ({@code RepelSpeed 5000}, {@code RepelTime 2250ms}, {@code RepelDistance 32 tiles}). */
  public static final RepelConfig DEFAULTS = new RepelConfig(5000, 2250L, 32.0);
}
