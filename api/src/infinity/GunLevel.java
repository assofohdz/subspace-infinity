// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity;

/**
 * Gun level identity (1-4) — wire-protocol enum used by {@code GunStats},
 * {@code GunCurrentLevel} / {@code GunMaxLevel}. Sprite-sheet offsets live
 * in {@code infinity.client.view.GunVisuals}, kept out of the api layer per
 * {@code api-contracts.md}.
 *
 * @author Asser
 */
public enum GunLevel {
  LEVEL_1(1),
  LEVEL_2(2),
  LEVEL_3(3),
  LEVEL_4(4);

  /** Level value (1-4). */
  public final int level;

  GunLevel(final int level) {
    this.level = level;
  }

  /** Next gun level, or null at the maximum. */
  public GunLevel next() {
    final GunLevel[] vals = values();
    final int n = ordinal() + 1;
    return n < vals.length ? vals[n] : null;
  }
}
