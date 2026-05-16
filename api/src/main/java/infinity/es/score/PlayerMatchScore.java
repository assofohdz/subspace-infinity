// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import com.simsilica.es.EntityComponent;

/** Per-player score accumulating across the current match (sum of rounds). Resets on {@code ScoreReset(MATCH)}; canonical writer is {@code ScoreCoordinatorSystem}. */
public final class PlayerMatchScore implements EntityComponent {

  private final int value;

  public PlayerMatchScore() {
    this(0);
  }

  public PlayerMatchScore(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
