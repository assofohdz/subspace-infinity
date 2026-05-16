// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Per-arena tuning for {@code FillUpXTeams}. {@code teams == 0} signals
 * "use {@link #DEFAULT_TEAMS}" — lets {@code mechanic 'fill-up-x-teams'} (no
 * kwargs) bind without a Jackson default.
 */
public record FillUpXTeamsConfig(int teams) {

  public static final int DEFAULT_TEAMS = 2;

  public FillUpXTeamsConfig {
    if (teams < 0) {
      throw new IllegalArgumentException("teams must be >= 0; got " + teams);
    }
  }

  /** {@link #teams} if {@code > 0}; otherwise {@link #DEFAULT_TEAMS}. */
  public int effectiveTeams() {
    return teams > 0 ? teams : DEFAULT_TEAMS;
  }
}
