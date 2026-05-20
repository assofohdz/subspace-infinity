// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Per-arena tuning for {@code FlagHoldTimeScoring}. {@code perSecondPerFlag} is the
 * team-score delta accumulated per second per owned flag (e.g. {@code 5} = each owned
 * flag adds 5 points/sec to the holding team). Validated via compact constructor.
 */
public record FlagHoldTimeConfig(int perSecondPerFlag) {

  public static final int DEFAULT_PER_SECOND_PER_FLAG = 5;

  public FlagHoldTimeConfig {
    if (perSecondPerFlag < 0) {
      throw new IllegalArgumentException(
          "perSecondPerFlag must be >= 0; got " + perSecondPerFlag);
    }
  }

  public FlagHoldTimeConfig() {
    this(DEFAULT_PER_SECOND_PER_FLAG);
  }

  /** Effective rate — returns {@link #DEFAULT_PER_SECOND_PER_FLAG} when {@code perSecondPerFlag == 0}. */
  public int effectivePerSecondPerFlag() {
    return perSecondPerFlag > 0 ? perSecondPerFlag : DEFAULT_PER_SECOND_PER_FLAG;
  }
}
