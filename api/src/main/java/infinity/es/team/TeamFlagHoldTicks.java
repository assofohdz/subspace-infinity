// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.team;

import com.simsilica.es.EntityComponent;

/**
 * On a {@link TeamEntity}: cumulative flag-hold ticks for the team during the current round.
 * Canonical writer is {@code FlagHoldTimeScoring}; reset to 0 on round-end. Read by
 * {@code MostFlagOccupancyWinCondition} to pick the round winner.
 *
 * <p>Class-not-record per the wire-crossing convention (jME3 FieldSerializer can't set record fields).
 */
public final class TeamFlagHoldTicks implements EntityComponent {

  private final int ticks;

  public TeamFlagHoldTicks() {
    this(0);
  }

  public TeamFlagHoldTicks(final int ticks) {
    this.ticks = ticks;
  }

  public int ticks() {
    return ticks;
  }
}
