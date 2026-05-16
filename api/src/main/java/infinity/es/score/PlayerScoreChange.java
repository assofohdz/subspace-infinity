// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import infinity.es.DeltaChange;

/** Additive delta to {@link PlayerRoundScore}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code ScoreCoordinatorSystem}. See ADR 0001. */
public record PlayerScoreChange(int delta) implements DeltaChange {

  public PlayerScoreChange() {
    this(0);
  }
}
