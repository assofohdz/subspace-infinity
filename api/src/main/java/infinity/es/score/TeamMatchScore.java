// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import com.simsilica.es.EntityComponent;

/** Per-team score for the current match on the team entity; canonical writer is {@code ScoreCoordinatorSystem}. Resets to 0 on match-end. */
public final class TeamMatchScore implements EntityComponent {

  private final int value;

  public TeamMatchScore() {
    this(0);
  }

  public TeamMatchScore(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
