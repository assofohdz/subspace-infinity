// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity;

/** Bullet level identity (1-4); wire-protocol enum used by {@code BulletStats}, {@code BulletCurrentLevel}, etc. */
public enum BulletLevel {
  LEVEL_1(1),
  LEVEL_2(2),
  LEVEL_3(3),
  LEVEL_4(4);

  /** Level value (1-4). */
  public final int level;

  BulletLevel(final int level) {
    this.level = level;
  }

  /** Next bullet level, or null at the maximum. */
  public BulletLevel next() {
    final BulletLevel[] vals = values();
    final int n = ordinal() + 1;
    return n < vals.length ? vals[n] : null;
  }
}
