// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Per-arena tuning for {@code CrownKillBonus}. {@code perCrownKill} multiplies the
 * killer's current crown count; e.g. a killer with 3 crowns making a kill gets
 * {@code 3 * perCrownKill} extra points on top of the base {@code KillPointsScoring}
 * award.
 */
public record CrownKillBonusConfig(int perCrownKill) {

  public static final int DEFAULT_PER_CROWN_KILL = 50;

  public CrownKillBonusConfig {
    if (perCrownKill < 0) {
      throw new IllegalArgumentException(
          "perCrownKill must be >= 0; got " + perCrownKill);
    }
  }

  public CrownKillBonusConfig() {
    this(DEFAULT_PER_CROWN_KILL);
  }

  public int effectivePerCrownKill() {
    return perCrownKill > 0 ? perCrownKill : DEFAULT_PER_CROWN_KILL;
  }
}
