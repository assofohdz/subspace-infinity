// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Per-arena tuning for {@code FillUpXTeams}. {@code teams == 0} signals "use
 * {@link #DEFAULT_TEAMS}" — lets {@code mechanic 'fill-up-x-teams'} (no kwargs)
 * bind without a Jackson default. {@code countPerPlayer} additively scales the
 * total spawn target with active player count per {@code player-scaling.md}; default
 * {@code 0} preserves the historical no-scaling behaviour.
 */
public record FillUpXTeamsConfig(int teams, int countPerPlayer) {

  public static final int DEFAULT_TEAMS = 2;

  public FillUpXTeamsConfig {
    if (teams < 0) {
      throw new IllegalArgumentException("teams must be >= 0; got " + teams);
    }
    if (countPerPlayer < 0) {
      throw new IllegalArgumentException("countPerPlayer must be >= 0; got " + countPerPlayer);
    }
  }

  /** Back-compat constructor for arenas / tests that don't specify scaling. */
  public FillUpXTeamsConfig(final int teams) {
    this(teams, 0);
  }

  /** {@link #teams} if {@code > 0}; otherwise {@link #DEFAULT_TEAMS}. */
  public int effectiveTeams() {
    return teams > 0 ? teams : DEFAULT_TEAMS;
  }
}
