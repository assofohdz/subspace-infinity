// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import com.simsilica.es.EntityComponent;

/** Per-player score for the current round; canonical writer is {@code ScoreCoordinatorSystem}. Resets to 0 on round-end (F2b). */
public final class PlayerRoundScore implements EntityComponent {

  private final int value;

  public PlayerRoundScore() {
    this(0);
  }

  public PlayerRoundScore(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
