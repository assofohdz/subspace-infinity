// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import infinity.es.CrownHolder;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.modules.ModuleContext;
import infinity.modules.WinConditionModule;
import infinity.modules.WinnerDeclaration;
import java.util.Optional;

/**
 * Dual-role KOTH winCondition. <b>Terminator:</b> per-tick, when the in-arena
 * {@link CrownHolder} set shrinks to exactly one holder, caches that ship's
 * {@link Frequency} and returns the round-end reason. <b>Decider:</b>
 * {@code declareWinner} returns the cached survivor freq, or
 * {@link WinnerDeclaration#UNDECIDED} when more than one holder remained at
 * round-end (timer fired first — fallback decider takes over).
 *
 * <p>Re-arms on {@code onRoundStart}. Foreign-arena entities filtered by
 * {@link ArenaId} equality.
 */
public final class LastCrownStandingWinCondition implements WinConditionModule {

  private final ArenaId arenaId;
  private final EntitySet crownedShips;
  private int cachedWinnerFreq = -1;

  public LastCrownStandingWinCondition(final ModuleContext ctx) {
    this.arenaId = ctx.arenaId();
    final EntityData ed = ctx.ed();
    this.crownedShips = ed.getEntities(ArenaId.class, CrownHolder.class, Frequency.class);
  }

  @Override
  public void onRoundStart(final ArenaId startedArenaId, final int roundNumber) {
    cachedWinnerFreq = -1;
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    crownedShips.release();
  }

  @Override
  public Optional<String> checkTermination(final ArenaId queryArenaId) {
    if (cachedWinnerFreq >= 0) {
      return Optional.of("last crown standing");
    }
    crownedShips.applyChanges();
    int holders = 0;
    int candidateFreq = -1;
    for (final Entity ship : crownedShips) {
      if (!arenaId.equals(ship.get(ArenaId.class))) {
        continue;
      }
      holders++;
      if (holders > 1) {
        return Optional.empty();
      }
      candidateFreq = ship.get(Frequency.class).getFrequency();
    }
    if (holders == 1) {
      cachedWinnerFreq = candidateFreq;
      return Optional.of("last crown standing");
    }
    return Optional.empty();
  }

  @Override
  public WinnerDeclaration declareWinner(final ArenaId queryArenaId) {
    if (cachedWinnerFreq < 0) {
      return WinnerDeclaration.UNDECIDED;
    }
    return new WinnerDeclaration(cachedWinnerFreq, "last crown standing");
  }
}
