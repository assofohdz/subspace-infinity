// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.event.EventBus;
import infinity.config.KillPointsConfig;
import infinity.es.ChangeTarget;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerScoreChange;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ModuleContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Pins {@link KillPointsScoring}'s emit + arenaId-filter semantics. */
public final class KillPointsScoringTest {

  private DefaultEntityData ed;
  private ArenaId thisArena;
  private ArenaId otherArena;
  private KillPointsScoring module;
  private EntitySet changes;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    thisArena = new ArenaId("ffa", ed.createEntity());
    otherArena = new ArenaId("trench", ed.createEntity());
    module = new KillPointsScoring(
        new ModuleContext(thisArena, ed.createEntity(), ed, null), new KillPointsConfig(100));
    changes = ed.getEntities(PlayerScoreChange.class, ChangeTarget.class);
    module.onArenaLoad(thisArena);
  }

  @After
  public void tearDown() {
    module.onArenaUnload(thisArena);
    changes.release();
  }

  @Test
  public void killInThisArena_emitsScoreChangeForKiller() {
    final EntityId killer = ed.createEntity();
    final EntityId victim = ed.createEntity();
    ed.setComponent(killer, thisArena);
    ed.setComponent(victim, thisArena);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, killer, (byte) 0));

    changes.applyChanges();
    assertEquals("one transient created", 1, changes.size());
    final var entity = changes.iterator().next();
    assertEquals(killer, entity.get(ChangeTarget.class).target());
    assertEquals(100, entity.get(PlayerScoreChange.class).delta());
  }

  @Test
  public void killInOtherArena_doesNotEmit() {
    final EntityId killer = ed.createEntity();
    final EntityId victim = ed.createEntity();
    ed.setComponent(killer, otherArena);
    ed.setComponent(victim, otherArena);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, killer, (byte) 0));

    changes.applyChanges();
    assertEquals("foreign-arena kill ignored", 0, changes.size());
  }

  @Test
  public void unattributedDeath_doesNotEmit() {
    final EntityId victim = ed.createEntity();
    ed.setComponent(victim, thisArena);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, null, (byte) 0));

    changes.applyChanges();
    assertEquals("null killer ignored", 0, changes.size());
  }

  @Test
  public void killerWithoutArenaId_doesNotEmit() {
    final EntityId killer = ed.createEntity();
    final EntityId victim = ed.createEntity();
    // killer has no ArenaId component
    ed.setComponent(victim, thisArena);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, killer, (byte) 0));

    changes.applyChanges();
    assertNull(ed.getComponent(killer, ArenaId.class));
    assertEquals("killer without ArenaId ignored", 0, changes.size());
  }
}
