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
import java.util.HashMap;
import java.util.Map;

/**
 * Decider-only KOTH winCondition (fallback when the timer fires before
 * {@code LastCrownStandingWinCondition} can identify a sole survivor).
 * {@code declareWinner} sums {@link CrownHolder#crowns()} per
 * {@link Frequency} across this arena's holders and returns the highest.
 * {@link WinnerDeclaration#UNDECIDED} when no holders remain.
 */
public final class MostCrownsWinCondition implements WinConditionModule {

  private final ArenaId arenaId;
  private final EntitySet crownedShips;

  public MostCrownsWinCondition(final ModuleContext ctx) {
    this.arenaId = ctx.arenaId();
    final EntityData ed = ctx.ed();
    this.crownedShips = ed.getEntities(ArenaId.class, CrownHolder.class, Frequency.class);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    crownedShips.release();
  }

  @Override
  public WinnerDeclaration declareWinner(final ArenaId queryArenaId) {
    crownedShips.applyChanges();
    final Map<Integer, Integer> crownsByFreq = new HashMap<>();
    for (final Entity ship : crownedShips) {
      if (!arenaId.equals(ship.get(ArenaId.class))) {
        continue;
      }
      final int freq = ship.get(Frequency.class).getFrequency();
      final int crowns = ship.get(CrownHolder.class).crowns();
      crownsByFreq.merge(freq, crowns, Integer::sum);
    }
    int bestFreq = -1;
    int bestCrowns = 0;
    for (final Map.Entry<Integer, Integer> e : crownsByFreq.entrySet()) {
      if (e.getValue() > bestCrowns) {
        bestCrowns = e.getValue();
        bestFreq = e.getKey();
      }
    }
    if (bestFreq < 0) {
      return WinnerDeclaration.UNDECIDED;
    }
    return new WinnerDeclaration(bestFreq, "most crowns (" + bestCrowns + ")");
  }
}
