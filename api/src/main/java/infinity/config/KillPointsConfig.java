// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/** Per-arena tuning for {@code KillPointsScoring}. Validated via compact constructor. */
public record KillPointsConfig(int perKill) {

  public KillPointsConfig {
    if (perKill <= 0) {
      throw new IllegalArgumentException("perKill must be > 0; got " + perKill);
    }
  }
}
