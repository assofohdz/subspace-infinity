// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;

/** Sprite-sheet offsets for special-bomb variants (EMP, Super, Thor); only {@link #THOR} is currently wired. */
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
