// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;

/**
 * Client-side sprite-sheet offsets for the special-bomb variants (EMP, Super,
 * Thor). Relocated from the former api {@code BombRegistry} enum, which was
 * client-only data leaking into the api layer. Only {@link #THOR} is currently
 * referenced by {@code SISpatialFactory}; the EMP and Super variants are kept
 * as scaffolding for the planned weapon-variant feature (parallel to the
 * unwired one-shot sprite-shader stub).
 *
 * <p>The previous {@code lightColor} / {@code lightRadius} fields were dropped
 * — only the constructor assignments referenced them, no consumer ever read.
 */
public enum SpecialBombVisuals {
  EMP_1(1, 8),
  EMP_2(2, 7),
  EMP_3(3, 6),
  EMP_4(4, 5),
  SUPER_1(1, 4),
  SUPER_2(2, 3),
  SUPER_3(3, 2),
  SUPER_4(4, 1),
  THOR(1, 0);

  /** Variant level (1-4 within the EMP / Super families; 1 for Thor). */
  public final int level;

  /** Offset in the bm2 sprite sheet. */
  public final int viewOffset;

  SpecialBombVisuals(final int level, final int viewOffset) {
    this.level = level;
    this.viewOffset = viewOffset;
  }
}
