// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerRoundScore;
import infinity.modules.ModuleContext;
import infinity.modules.WinConditionModule;
import infinity.modules.WinnerDeclaration;

/**
 * Decider-only winCondition. On {@code declareWinner} reads {@link PlayerRoundScore}
 * across the arena's players, returns the freq of the highest scorer. Ties broken
 * by first-found. Returns {@link WinnerDeclaration#UNDECIDED} when no players have
 * a score yet.
 */
public final class HighestScoreWinCondition implements WinConditionModule {

  private final ArenaId arenaId;
  private final EntitySet scoredPlayers;

  public HighestScoreWinCondition(final ModuleContext ctx) {
    this.arenaId = ctx.arenaId();
    final EntityData ed = ctx.ed();
    this.scoredPlayers =
        ed.getEntities(ArenaId.class, PlayerRoundScore.class, Frequency.class);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    scoredPlayers.release();
  }

  @Override
  public WinnerDeclaration declareWinner(final ArenaId queryArenaId) {
    scoredPlayers.applyChanges();
    int bestScore = Integer.MIN_VALUE;
    int bestFreq = -1;
    for (final Entity player : scoredPlayers) {
      if (!arenaId.equals(player.get(ArenaId.class))) {
        continue;
      }
      final int score = player.get(PlayerRoundScore.class).getValue();
      if (score > bestScore) {
        bestScore = score;
        bestFreq = player.get(Frequency.class).getFrequency();
      }
    }
    if (bestFreq < 0) {
      return WinnerDeclaration.UNDECIDED;
    }
    return new WinnerDeclaration(bestFreq, "highest score (" + bestScore + ")");
  }
}
