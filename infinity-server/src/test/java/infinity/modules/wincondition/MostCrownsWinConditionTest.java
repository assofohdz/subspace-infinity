// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.CrownHolder;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.modules.ModuleContext;
import infinity.modules.WinnerDeclaration;
import org.junit.Before;
import org.junit.Test;

/** Pins {@link MostCrownsWinCondition}'s sum-per-freq decider. */
public final class MostCrownsWinConditionTest {

  private DefaultEntityData ed;
  private ArenaId arenaId;
  private MostCrownsWinCondition module;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    arenaId = new ArenaId("koth", ed.createEntity());
    module = new MostCrownsWinCondition(
        new ModuleContext(arenaId, arenaId.getOwner(), ed, null, null));
  }

  @Test
  public void noHolders_returnsUndecided() {
    assertEquals(WinnerDeclaration.UNDECIDED, module.declareWinner(arenaId));
  }

  @Test
  public void singleHolder_wins() {
    spawnHolder(3, 5);

    final WinnerDeclaration decl = module.declareWinner(arenaId);
    assertEquals(3, decl.winningFreq());
    assertTrue(decl.reason().contains("5"));
  }

  @Test
  public void summedAcrossFreq_highestSumWins() {
    spawnHolder(0, 2);
    spawnHolder(0, 3);
    spawnHolder(1, 4);

    // freq 0 has 5 crowns total; freq 1 has 4 — freq 0 wins.
    assertEquals(0, module.declareWinner(arenaId).winningFreq());
  }

  @Test
  public void foreignArenaHolders_ignored() {
    spawnHolder(0, 1);
    final EntityId foreign = ed.createEntity();
    ed.setComponent(foreign, new ArenaId("ffa", ed.createEntity()));
    ed.setComponent(foreign, new CrownHolder(99));
    ed.setComponent(foreign, new Frequency(7));

    assertEquals("foreign arena ignored",
        0, module.declareWinner(arenaId).winningFreq());
  }

  private EntityId spawnHolder(final int freq, final int crowns) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, arenaId);
    ed.setComponent(ship, new CrownHolder(crowns));
    ed.setComponent(ship, new Frequency(freq));
    return ship;
  }
}
