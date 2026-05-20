// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.scoring;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.event.EventBus;
import infinity.config.CrownKillBonusConfig;
import infinity.es.ChangeTarget;
import infinity.es.CrownHolder;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerScoreChange;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ModuleContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Pins {@link CrownKillBonus}: killer with crowns earns the bonus on kill; non-crowned killer earns nothing extra. */
public final class CrownKillBonusTest {

  private DefaultEntityData ed;
  private ArenaId arenaId;
  private CrownKillBonus module;
  private EntitySet scoreChanges;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    arenaId = new ArenaId("koth", ed.createEntity());
    module = new CrownKillBonus(
        new ModuleContext(arenaId, arenaId.getOwner(), ed, null, null),
        new CrownKillBonusConfig(50));
    module.onArenaLoad(arenaId);
    scoreChanges = ed.getEntities(PlayerScoreChange.class, ChangeTarget.class);
  }

  @After
  public void tearDown() {
    module.onArenaUnload(arenaId);
    scoreChanges.release();
  }

  @Test
  public void killerWithNoCrowns_emitsNoBonus() {
    final EntityId killer = ed.createEntity();
    final EntityId victim = ed.createEntity();
    ed.setComponent(killer, arenaId);
    ed.setComponent(victim, arenaId);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, killer, (byte) 0));

    scoreChanges.applyChanges();
    assertEquals(0, scoreChanges.size());
  }

  @Test
  public void killerWithOneCrown_emits50() {
    final EntityId killer = ed.createEntity();
    final EntityId victim = ed.createEntity();
    ed.setComponent(killer, arenaId);
    ed.setComponent(killer, new CrownHolder(1));
    ed.setComponent(victim, arenaId);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, killer, (byte) 0));

    scoreChanges.applyChanges();
    assertEquals(1, scoreChanges.size());
    final Entity intent = scoreChanges.iterator().next();
    assertEquals(killer, intent.get(ChangeTarget.class).target());
    assertEquals(50, intent.get(PlayerScoreChange.class).delta());
  }

  @Test
  public void killerWithThreeCrowns_emitsThreeTimesBase() {
    final EntityId killer = ed.createEntity();
    final EntityId victim = ed.createEntity();
    ed.setComponent(killer, arenaId);
    ed.setComponent(killer, new CrownHolder(3));
    ed.setComponent(victim, arenaId);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, killer, (byte) 0));

    scoreChanges.applyChanges();
    assertEquals(150, scoreChanges.iterator().next().get(PlayerScoreChange.class).delta());
  }

  @Test
  public void killWithoutKiller_emitsNothing() {
    final EntityId victim = ed.createEntity();
    ed.setComponent(victim, arenaId);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, null, (byte) 0));

    scoreChanges.applyChanges();
    assertEquals(0, scoreChanges.size());
  }

  @Test
  public void foreignArenaKiller_isIgnored() {
    final EntityId killer = ed.createEntity();
    ed.setComponent(killer, new ArenaId("ffa", ed.createEntity()));
    ed.setComponent(killer, new CrownHolder(5));
    final EntityId victim = ed.createEntity();
    ed.setComponent(victim, arenaId);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, killer, (byte) 0));

    scoreChanges.applyChanges();
    assertEquals("foreign-arena killer ignored", 0, scoreChanges.size());
  }
}
