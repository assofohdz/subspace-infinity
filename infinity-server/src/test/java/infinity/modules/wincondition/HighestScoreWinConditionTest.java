// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerRoundScore;
import infinity.modules.ModuleContext;
import infinity.modules.WinnerDeclaration;
import org.junit.Test;

/** Pins {@link HighestScoreWinCondition}'s pick-the-top-freq semantics. */
public final class HighestScoreWinConditionTest {

  private static final String FFA = "ffa";

  @Test
  public void noScoredPlayers_returnsUndecided() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId arenaId = new ArenaId(FFA,ed.createEntity());
    final HighestScoreWinCondition wc = build(ed, arenaId);

    assertEquals(WinnerDeclaration.UNDECIDED, wc.declareWinner(arenaId));
  }

  @Test
  public void singlePlayer_returnsTheirFreq() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId arenaId = new ArenaId(FFA,ed.createEntity());
    final EntityId p = ed.createEntity();
    ed.setComponent(p, arenaId);
    ed.setComponent(p, new PlayerRoundScore(50));
    ed.setComponent(p, new Frequency(7));
    final HighestScoreWinCondition wc = build(ed, arenaId);

    final WinnerDeclaration vote = wc.declareWinner(arenaId);
    assertEquals(7, vote.winningFreq());
  }

  @Test
  public void multiplePlayers_returnsHighestScorerFreq() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId arenaId = new ArenaId(FFA,ed.createEntity());
    scoredPlayer(ed, arenaId, /* freq */ 1, /* score */ 100);
    scoredPlayer(ed, arenaId, /* freq */ 2, /* score */ 250);
    scoredPlayer(ed, arenaId, /* freq */ 3, /* score */ 175);
    final HighestScoreWinCondition wc = build(ed, arenaId);

    assertEquals(2, wc.declareWinner(arenaId).winningFreq());
  }

  @Test
  public void foreignArenaPlayers_ignored() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId ffa = new ArenaId(FFA,ed.createEntity());
    final ArenaId trench = new ArenaId("trench", ed.createEntity());
    scoredPlayer(ed, ffa, /* freq */ 1, /* score */ 50);
    scoredPlayer(ed, trench, /* freq */ 99, /* score */ 99999);
    final HighestScoreWinCondition wc = build(ed, ffa);

    assertEquals("foreign arena's top scorer ignored",
        1, wc.declareWinner(ffa).winningFreq());
  }

  private static HighestScoreWinCondition build(
      final DefaultEntityData ed, final ArenaId arenaId) {
    return new HighestScoreWinCondition(
        new ModuleContext(arenaId, arenaId.getOwner(), ed, null, null));
  }

  private static void scoredPlayer(
      final DefaultEntityData ed, final ArenaId arenaId, final int freq, final int score) {
    final EntityId p = ed.createEntity();
    ed.setComponent(p, arenaId);
    ed.setComponent(p, new PlayerRoundScore(score));
    ed.setComponent(p, new Frequency(freq));
  }
}
