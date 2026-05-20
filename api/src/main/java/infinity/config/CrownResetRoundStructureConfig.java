// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/** Per-arena tuning for {@code CrownResetRoundStructure} (KOTH timer round length). */
public record CrownResetRoundStructureConfig(int minutes) {

  public CrownResetRoundStructureConfig {
    if (minutes <= 0) {
      throw new IllegalArgumentException("minutes must be > 0; got " + minutes);
    }
  }
}
