// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.config.FirstToXWinConditionConfig;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerRoundScore;
import infinity.modules.ModuleContext;
import infinity.modules.WinnerDeclaration;
import org.junit.Test;

/** Pins {@link FirstToXWinCondition}'s terminator + decider semantics. */
public final class FirstToXWinConditionTest {

  private static final String FFA = "ffa";

  @Test
  public void noPlayerReachesTarget_doesNotTerminate() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId arenaId = new ArenaId(FFA, ed.createEntity());
    scoredPlayer(ed, arenaId, 1, 500);
    scoredPlayer(ed, arenaId, 2, 800);
    final FirstToXWinCondition wc = build(ed, arenaId, 1000);

    assertFalse(wc.checkTermination(arenaId).isPresent());
    assertSame(WinnerDeclaration.UNDECIDED, wc.declareWinner(arenaId));
  }

  @Test
  public void playerReachesTarget_terminates_andDeclaresTheirFreq() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId arenaId = new ArenaId(FFA, ed.createEntity());
    scoredPlayer(ed, arenaId, 1, 500);
    scoredPlayer(ed, arenaId, 7, 1000);
    final FirstToXWinCondition wc = build(ed, arenaId, 1000);

    assertTrue(wc.checkTermination(arenaId).isPresent());
    assertEquals(7, wc.declareWinner(arenaId).winningFreq());
  }

  @Test
  public void noTargetKwarg_defaultsTo1000() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId arenaId = new ArenaId(FFA, ed.createEntity());
    scoredPlayer(ed, arenaId, 9, 999);
    final FirstToXWinCondition wc = build(ed, arenaId, /* target == 0 → default */ 0);

    assertFalse("999 < default 1000", wc.checkTermination(arenaId).isPresent());

    scoredPlayer(ed, arenaId, 10, 1000);
    assertTrue("1000 reaches default", wc.checkTermination(arenaId).isPresent());
    assertEquals(10, wc.declareWinner(arenaId).winningFreq());
  }

  @Test
  public void onRoundStart_clearsCache() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId arenaId = new ArenaId(FFA, ed.createEntity());
    final EntityId p = ed.createEntity();
    ed.setComponent(p, arenaId);
    ed.setComponent(p, new PlayerRoundScore(1000));
    ed.setComponent(p, new Frequency(5));
    final FirstToXWinCondition wc = build(ed, arenaId, 1000);

    // Round 1: player reaches target → cache set → declareWinner returns 5.
    assertTrue(wc.checkTermination(arenaId).isPresent());
    assertEquals(5, wc.declareWinner(arenaId).winningFreq());

    // Simulate dispatcher's round-end cycle: ScoreReset zeroed the score; new round starts.
    ed.setComponent(p, new PlayerRoundScore(0));
    wc.onRoundStart(arenaId, 2);

    assertSame("cache cleared on onRoundStart", WinnerDeclaration.UNDECIDED, wc.declareWinner(arenaId));
    assertFalse("no one's reached target in the new round",
        wc.checkTermination(arenaId).isPresent());
  }

  @Test
  public void foreignArenaPlayers_ignored() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId ffa = new ArenaId(FFA, ed.createEntity());
    final ArenaId trench = new ArenaId("trench", ed.createEntity());
    scoredPlayer(ed, trench, 99, 9999);
    scoredPlayer(ed, ffa, 1, 500);
    final FirstToXWinCondition wc = build(ed, ffa, 1000);

    assertFalse("trench's 9999 ignored", wc.checkTermination(ffa).isPresent());
  }

  private static FirstToXWinCondition build(
      final DefaultEntityData ed, final ArenaId arenaId, final int target) {
    return new FirstToXWinCondition(
        new ModuleContext(arenaId, arenaId.getOwner(), ed),
        new FirstToXWinConditionConfig(target));
  }

  private static void scoredPlayer(
      final DefaultEntityData ed, final ArenaId arenaId, final int freq, final int score) {
    final EntityId p = ed.createEntity();
    ed.setComponent(p, arenaId);
    ed.setComponent(p, new PlayerRoundScore(score));
    ed.setComponent(p, new Frequency(freq));
  }
}
