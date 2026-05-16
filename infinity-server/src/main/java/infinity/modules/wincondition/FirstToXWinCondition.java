// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import infinity.config.FirstToXWinConditionConfig;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerRoundScore;
import infinity.modules.ModuleContext;
import infinity.modules.WinConditionModule;
import infinity.modules.WinnerDeclaration;
import java.util.Optional;

/**
 * Dual-role winCondition. <b>Terminator:</b> per-tick scans players in the arena
 * for {@code PlayerRoundScore >= target}; if any, caches the winning freq and
 * returns the round-end reason. <b>Decider:</b> {@code declareWinner} returns
 * the cached winning freq, or {@link WinnerDeclaration#UNDECIDED} when no one
 * has reached the target.
 *
 * <p>Re-arms on {@code onRoundStart} (cache cleared). Foreign-arena entities are
 * filtered by {@link ArenaId} equality.
 */
public final class FirstToXWinCondition implements WinConditionModule {

  private final ArenaId arenaId;
  private final int target;
  private final EntitySet scoredPlayers;
  private int cachedWinnerFreq = -1;

  public FirstToXWinCondition(final ModuleContext ctx, final FirstToXWinConditionConfig config) {
    this.arenaId = ctx.arenaId();
    this.target = config.effectiveTarget();
    final EntityData ed = ctx.ed();
    this.scoredPlayers =
        ed.getEntities(ArenaId.class, PlayerRoundScore.class, Frequency.class);
  }

  @Override
  public void onRoundStart(final ArenaId startedArenaId, final int roundNumber) {
    cachedWinnerFreq = -1;
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    scoredPlayers.release();
  }

  @Override
  public Optional<String> checkTermination(final ArenaId queryArenaId) {
    if (cachedWinnerFreq >= 0) {
      // Already triggered this round; sticky until onRoundStart clears it.
      return Optional.of("first to " + target + " pts");
    }
    scoredPlayers.applyChanges();
    for (final Entity player : scoredPlayers) {
      if (!arenaId.equals(player.get(ArenaId.class))) {
        continue;
      }
      if (player.get(PlayerRoundScore.class).getValue() >= target) {
        cachedWinnerFreq = player.get(Frequency.class).getFrequency();
        return Optional.of("first to " + target + " pts");
      }
    }
    return Optional.empty();
  }

  @Override
  public WinnerDeclaration declareWinner(final ArenaId queryArenaId) {
    if (cachedWinnerFreq < 0) {
      return WinnerDeclaration.UNDECIDED;
    }
    return new WinnerDeclaration(cachedWinnerFreq, "first to " + target + " pts");
  }
}
