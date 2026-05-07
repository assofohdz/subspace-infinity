// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;

import infinity.BulletLevel;

/**
 * Client-side sprite-sheet offsets for each {@link BulletLevel} level. Pure render
 * data — kept out of the api {@code BulletLevel} enum so the wire-protocol identity
 * stays free of jME-side concerns.
 */
public enum BulletVisuals {
  LEVEL_1(BulletLevel.LEVEL_1, 9),
  LEVEL_2(BulletLevel.LEVEL_2, 8),
  LEVEL_3(BulletLevel.LEVEL_3, 7),
  LEVEL_4(BulletLevel.LEVEL_4, 6);

  public final BulletLevel level;
  public final int viewOffset;

  BulletVisuals(final BulletLevel level, final int viewOffset) {
    this.level = level;
    this.viewOffset = viewOffset;
  }

  /** Look up the visuals for a given bullet level. */
  public static BulletVisuals forLevel(final BulletLevel level) {
    for (final BulletVisuals v : values()) {
      if (v.level == level) {
        return v;
      }
    }
    throw new IllegalArgumentException("No BulletVisuals for " + level);
  }
}
