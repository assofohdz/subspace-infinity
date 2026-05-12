// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity;

/** Bomb level identity (1-4); wire-protocol enum used by {@code BombStats}, {@code BombCurrentLevel}, etc. */
public enum BombLevel {
  BOMB_1(1),
  BOMB_2(2),
  BOMB_3(3),
  BOMB_4(4);

  /** Level value (1-4). */
  public final int level;

  BombLevel(final int level) {
    this.level = level;
  }

  /** Next bomb level, or null at the maximum. */
  public BombLevel next() {
    final BombLevel[] vals = values();
    final int n = ordinal() + 1;
    return n < vals.length ? vals[n] : null;
  }
}
