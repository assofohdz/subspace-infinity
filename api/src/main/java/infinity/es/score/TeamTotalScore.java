// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import com.simsilica.es.EntityComponent;

/** Per-team total score across the arena session on the team entity; canonical writer is {@code ScoreCoordinatorSystem}. Never resets. */
public final class TeamTotalScore implements EntityComponent {

  private final int value;

  public TeamTotalScore() {
    this(0);
  }

  public TeamTotalScore(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
