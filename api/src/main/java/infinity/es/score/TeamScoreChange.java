// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import infinity.es.DeltaChange;

/** Additive delta to {@link TeamRoundScore} / {@link TeamMatchScore} / {@link TeamTotalScore}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code ScoreCoordinatorSystem}. See ADR 0001. */
public record TeamScoreChange(int delta) implements DeltaChange {

  public TeamScoreChange() {
    this(0);
  }
}
