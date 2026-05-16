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
import infinity.es.score.PlayerRoundScore;
import infinity.es.score.PlayerScoreChange;
import org.junit.Test;

/** Pins {@link ScoreCoordinatorSystem}'s drain semantics. */
public final class ScoreCoordinatorSystemTest {

  @Test
  public void emptyPriorScore_writesDelta() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId player = ed.createEntity();
      final EntityId change = emit(ed, player, 100);

      systems.update();

      assertEquals(100, ed.getComponent(player, PlayerRoundScore.class).getValue());
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
