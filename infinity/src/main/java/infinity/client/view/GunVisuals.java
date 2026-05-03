// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;

import infinity.GunLevel;

/**
 * Client-side sprite-sheet offsets for each {@link GunLevel} level. Pure render
 * data — kept out of the api {@code GunLevel} enum so the wire-protocol identity
 * stays free of jME-side concerns.
 */
public enum GunVisuals {
  LEVEL_1(GunLevel.LEVEL_1, 9),
  LEVEL_2(GunLevel.LEVEL_2, 8),
  LEVEL_3(GunLevel.LEVEL_3, 7),
  LEVEL_4(GunLevel.LEVEL_4, 6);

  public final GunLevel level;
  public final int viewOffset;

  GunVisuals(final GunLevel level, final int viewOffset) {
    this.level = level;
    this.viewOffset = viewOffset;
  }

  /** Look up the visuals for a given gun level. */
  public static GunVisuals forLevel(final GunLevel level) {
    for (final GunVisuals v : values()) {
      if (v.level == level) {
        return v;
      }
    }
    throw new IllegalArgumentException("No GunVisuals for " + level);
  }
}
