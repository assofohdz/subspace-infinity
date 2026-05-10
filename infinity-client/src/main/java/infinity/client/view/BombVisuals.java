// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;

import infinity.BombLevel;

/**
 * Client-side sprite-sheet offsets for each {@link BombLevel} level. Pure render
 * data — kept out of the api {@code BombLevel} enum so the wire-protocol identity
 * stays free of jME-side concerns. (The previous {@code lightColor} /
 * {@code lightRadius} fields on {@code BombLevel} were dropped — only the
 * commented-out {@code GameEntities} call referenced them, and the live
 * lighting pipeline uses {@code SISpatialFactory}'s own logic.)
 */
public enum BombVisuals {
  BOMB_1(BombLevel.BOMB_1, 12),
  BOMB_2(BombLevel.BOMB_2, 11),
  BOMB_3(BombLevel.BOMB_3, 10),
  BOMB_4(BombLevel.BOMB_4, 9);

  public final BombLevel level;
  public final int viewOffset;

  BombVisuals(final BombLevel level, final int viewOffset) {
    this.level = level;
    this.viewOffset = viewOffset;
  }

  /** Look up the visuals for a given bomb level. */
  public static BombVisuals forLevel(final BombLevel level) {
    for (final BombVisuals v : values()) {
      if (v.level == level) {
        return v;
      }
    }
    throw new IllegalArgumentException("No BombVisuals for " + level);
  }
}
