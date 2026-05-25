// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Per-arena tuning for {@code FillUpXTeams}. {@code teams == 0} / {@code capacity == 0}
 * signal "use the default" — lets {@code mechanic 'fill-up-x-teams'} (no kwargs) bind
 * without a Jackson default. {@code countPerPlayer} additively scales the spawn target
 * with active player count per {@code player-scaling.md}; default {@code 0} preserves the
 * historical no-scaling behaviour.
 *
 * <p>The two count knobs apply in disjoint team-setup modes (see
 * {@code TeamSetupModule.fixedTeamCount()}): {@code capacity} is the per-team seat target
 * in a <em>bounded</em> setup (e.g. two-fixed-teams) — bots fill the seats humans don't,
 * total target {@code fixedTeamCount × capacity}. {@code teams} is the flat bot-count
 * target in an <em>unbounded</em> (FFA) setup, where a non-empty {@code bots {}} roster
 * overrides it.
 */
public record FillUpXTeamsConfig(int teams, int countPerPlayer, int capacity) {

  public static final int DEFAULT_TEAMS = 2;
  public static final int DEFAULT_CAPACITY = 1;

  public FillUpXTeamsConfig {
    if (teams < 0) {
      throw new IllegalArgumentException("teams must be >= 0; got " + teams);
    }
    if (countPerPlayer < 0) {
      throw new IllegalArgumentException("countPerPlayer must be >= 0; got " + countPerPlayer);
    }
    if (capacity < 0) {
      throw new IllegalArgumentException("capacity must be >= 0; got " + capacity);
    }
  }

  /** Back-compat constructor for arenas / tests that don't specify scaling or capacity. */
  public FillUpXTeamsConfig(final int teams) {
    this(teams, 0, 0);
  }

  /** Back-compat constructor for arenas / tests that don't specify capacity. */
  public FillUpXTeamsConfig(final int teams, final int countPerPlayer) {
    this(teams, countPerPlayer, 0);
  }

  /** {@link #teams} if {@code > 0}; otherwise {@link #DEFAULT_TEAMS}. */
  public int effectiveTeams() {
    return teams > 0 ? teams : DEFAULT_TEAMS;
  }

  /** {@link #capacity} if {@code > 0}; otherwise {@link #DEFAULT_CAPACITY}. */
  public int effectiveCapacity() {
    return capacity > 0 ? capacity : DEFAULT_CAPACITY;
  }
}
