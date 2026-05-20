// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.team.TeamEntity;
import infinity.es.team.TeamFlagHoldTicks;
import infinity.modules.ModuleContext;
import infinity.modules.WinConditionModule;
import infinity.modules.WinnerDeclaration;

/**
 * Decider-only win condition. {@link #declareWinner} reads {@link TeamFlagHoldTicks}
 * across the arena's teams, returns the freq of the team with the most occupancy this
 * round. Ties broken by first-found (deterministic insertion order). Returns
 * {@link WinnerDeclaration#UNDECIDED} when no team has any flag-hold time yet.
 */
public final class MostFlagOccupancyWinCondition implements WinConditionModule {

  private final ArenaId arenaId;
  private final EntitySet teams;

  public MostFlagOccupancyWinCondition(final ModuleContext ctx) {
    this.arenaId = ctx.arenaId();
    final EntityData ed = ctx.ed();
    this.teams = ed.getEntities(
        TeamEntity.class, ArenaId.class, Frequency.class, TeamFlagHoldTicks.class);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    teams.release();
  }

  @Override
  public WinnerDeclaration declareWinner(final ArenaId queryArenaId) {
    teams.applyChanges();
    int bestTicks = 0;
    int bestFreq = -1;
    for (final Entity team : teams) {
      if (!arenaId.equals(team.get(ArenaId.class))) {
        continue;
      }
      final int ticks = team.get(TeamFlagHoldTicks.class).ticks();
      if (ticks > bestTicks) {
        bestTicks = ticks;
        bestFreq = team.get(Frequency.class).getFrequency();
      }
    }
    if (bestFreq < 0) {
      return WinnerDeclaration.UNDECIDED;
    }
    return new WinnerDeclaration(bestFreq, "most flag occupancy (" + bestTicks + " ticks)");
  }
}
