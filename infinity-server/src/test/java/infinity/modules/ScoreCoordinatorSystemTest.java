// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerMatchScore;
import infinity.es.score.PlayerRoundScore;
import infinity.es.score.PlayerScoreChange;
import infinity.es.score.PlayerTotalScore;
import infinity.es.score.ScoreReset;
import org.junit.Test;

/** Pins {@link ScoreCoordinatorSystem}'s drain semantics. */
public final class ScoreCoordinatorSystemTest {

  @Test
  public void emptyPriorScore_writesAllThreeTiers() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId player = ed.createEntity();
      final EntityId change = emit(ed, player, 100);

      systems.update();

      assertEquals(100, ed.getComponent(player, PlayerRoundScore.class).getValue());
      assertEquals(100, ed.getComponent(player, PlayerMatchScore.class).getValue());
      assertEquals(100, ed.getComponent(player, PlayerTotalScore.class).getValue());
      assertNull("one-shot drain destroyed the change entity",
          ed.getComponent(change, PlayerScoreChange.class));
    } finally {
      stop(systems);
    }
  }

  @Test
  public void priorScore_addsDelta() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId player = ed.createEntity();
      ed.setComponent(player, new PlayerRoundScore(250));
      emit(ed, player, 100);

      systems.update();

      assertEquals(350, ed.getComponent(player, PlayerRoundScore.class).getValue());
    } finally {
      stop(systems);
    }
  }

  @Test
  public void multipleDeltasSameTick_sumPerTarget() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId player = ed.createEntity();
      emit(ed, player, 100);
      emit(ed, player, 50);
      emit(ed, player, -25);

      systems.update();

      assertEquals(125, ed.getComponent(player, PlayerRoundScore.class).getValue());
    } finally {
      stop(systems);
    }
  }

  @Test
  public void roundReset_zeroesAllArenaPlayers() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId arenaEntity = ed.createEntity();
      final ArenaId arenaId = new ArenaId("ffa", arenaEntity);
      final EntityId p1 = ed.createEntity();
      final EntityId p2 = ed.createEntity();
      ed.setComponent(p1, arenaId);
      ed.setComponent(p1, new PlayerRoundScore(300));
      ed.setComponent(p2, arenaId);
      ed.setComponent(p2, new PlayerRoundScore(150));

      ed.setComponent(arenaEntity, arenaId);
      ed.setComponent(arenaEntity, new ScoreReset(ScoreReset.Scope.ROUND));

      systems.update();

      assertEquals(0, ed.getComponent(p1, PlayerRoundScore.class).getValue());
      assertEquals(0, ed.getComponent(p2, PlayerRoundScore.class).getValue());
      assertNull("ScoreReset marker drained",
          ed.getComponent(arenaEntity, ScoreReset.class));
    } finally {
      stop(systems);
    }
  }

  @Test
  public void matchReset_zeroesMatchAndRound_preservesTotal() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId arenaEntity = ed.createEntity();
      final ArenaId arenaId = new ArenaId("ffa", arenaEntity);
      final EntityId player = ed.createEntity();
      ed.setComponent(player, arenaId);
      ed.setComponent(player, new PlayerRoundScore(120));
      ed.setComponent(player, new PlayerMatchScore(420));
      ed.setComponent(player, new PlayerTotalScore(900));

      ed.setComponent(arenaEntity, arenaId);
      ed.setComponent(arenaEntity, new ScoreReset(ScoreReset.Scope.MATCH));

      systems.update();

      assertEquals("MATCH reset zeros match score",
          0, ed.getComponent(player, PlayerMatchScore.class).getValue());
      assertEquals("MATCH reset cascades to round (match contains round)",
          0, ed.getComponent(player, PlayerRoundScore.class).getValue());
      assertEquals("MATCH reset preserves total score (totals never reset)",
          900, ed.getComponent(player, PlayerTotalScore.class).getValue());
    } finally {
      stop(systems);
    }
  }

  @Test
  public void roundReset_otherArenaPlayersUnaffected() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ffaEntity = ed.createEntity();
      final EntityId trenchEntity = ed.createEntity();
      final ArenaId ffa = new ArenaId("ffa", ffaEntity);
      final ArenaId trench = new ArenaId("trench", trenchEntity);
      final EntityId pFfa = ed.createEntity();
      final EntityId pTrench = ed.createEntity();
      ed.setComponent(pFfa, ffa);
      ed.setComponent(pFfa, new PlayerRoundScore(500));
      ed.setComponent(pTrench, trench);
      ed.setComponent(pTrench, new PlayerRoundScore(700));

      ed.setComponent(ffaEntity, ffa);
      ed.setComponent(ffaEntity, new ScoreReset(ScoreReset.Scope.ROUND));

      systems.update();

      assertEquals(0, ed.getComponent(pFfa, PlayerRoundScore.class).getValue());
      assertEquals("foreign arena untouched",
          700, ed.getComponent(pTrench, PlayerRoundScore.class).getValue());
    } finally {
      stop(systems);
    }
  }

  private static void registerSystems(final GameSystemManager systems, final EntityData ed) {
    systems.register(EntityData.class, ed);
    systems.register(ScoreCoordinatorSystem.class, new ScoreCoordinatorSystem());
    systems.initialize();
    systems.start();
  }

  private static void stop(final GameSystemManager systems) {
    systems.stop();
    systems.terminate();
  }

  private static EntityId emit(final EntityData ed, final EntityId target, final int delta) {
    final EntityId change = ed.createEntity();
    ed.setComponents(change, new ChangeTarget(target, target), new PlayerScoreChange(delta));
    return change;
  }
}
