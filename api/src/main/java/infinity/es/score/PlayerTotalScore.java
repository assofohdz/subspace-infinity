// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import com.simsilica.es.EntityComponent;

/** Per-player score accumulating across the arena session — never reset; canonical writer is {@code ScoreCoordinatorSystem}. */
public final class PlayerTotalScore implements EntityComponent {

  private final int value;

  public PlayerTotalScore() {
    this(0);
  }

  public PlayerTotalScore(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
