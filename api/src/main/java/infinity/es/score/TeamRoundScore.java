// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import com.simsilica.es.EntityComponent;

/** Per-team score for the current round on the team entity; canonical writer is {@code ScoreCoordinatorSystem}. Resets to 0 on round-end (F2b). */
public final class TeamRoundScore implements EntityComponent {

  private final int value;

  public TeamRoundScore() {
    this(0);
  }

  public TeamRoundScore(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
