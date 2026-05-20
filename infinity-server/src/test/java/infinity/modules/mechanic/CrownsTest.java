// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.mechanic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.Ship;
import infinity.es.CrownHolder;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.ship.PlayerShip;
import infinity.es.ship.ShipType;
import com.simsilica.event.EventBus;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ModuleContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins {@link Crowns} mechanic semantics: distribute at round-start, transfer-on-kill,
 * drop-on-unattributed-kill, clear-at-round-end.
 */
public final class CrownsTest {

  private static final String ARENA_NAME = "koth";

  private DefaultEntityData ed;
  private ArenaId arenaId;
  private Crowns crowns;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    crowns = new Crowns(new ModuleContext(arenaId, arenaEntity, ed, null, null));
    crowns.onArenaLoad(arenaId);
  }

  @After
  public void tearDown() {
    crowns.onArenaUnload(arenaId);
  }

  @Test
  public void onRoundStart_distributesOneCrownPerActivePlayerInArena() {
    final EntityId a = spawnPlayer(arenaId);
    final EntityId b = spawnPlayerInOtherArena();
    final EntityId c = spawnPlayer(arenaId);

    crowns.onRoundStart(arenaId, 1);

    assertEquals(1, ed.getComponent(a, CrownHolder.class).crowns());
    assertEquals(1, ed.getComponent(c, CrownHolder.class).crowns());
    assertNull("foreign-arena player gets no crown",
        ed.getComponent(b, CrownHolder.class));
  }

  @Test
  public void killWithKnownKiller_transfersAllCrownsToKiller() {
    final EntityId victim = spawnPlayer(arenaId);
    final EntityId killer = spawnPlayer(arenaId);
    crowns.onRoundStart(arenaId, 1);
    // killer + victim each have 1 crown after distribute.

    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(victim, killer, (byte) 0));

    assertNull("victim's crown removed",
        ed.getComponent(victim, CrownHolder.class));
    assertEquals("killer accumulated victim's crown",
        2, ed.getComponent(killer, CrownHolder.class).crowns());
  }

  @Test
  public void killWithoutKiller_crownsDrop_noTransfer() {
    final EntityId victim = spawnPlayer(arenaId);
    crowns.onRoundStart(arenaId, 1);
    assertEquals(1, ed.getComponent(victim, CrownHolder.class).crowns());

    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(victim, null, (byte) 0));

    assertNull("crown dropped (component removed)",
        ed.getComponent(victim, CrownHolder.class));
  }

  @Test
  public void selfKill_dropsOwnCrown_doesNotSelfTransfer() {
    final EntityId player = spawnPlayer(arenaId);
    crowns.onRoundStart(arenaId, 1);

    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(player, player, (byte) 0));

    assertNull("self-kill drops the crown",
        ed.getComponent(player, CrownHolder.class));
  }

  @Test
  public void foreignArenaKill_isIgnored() {
    final EntityId victim = spawnPlayerInOtherArena();
    final EntityId killer = spawnPlayer(arenaId);
    ed.setComponent(victim, new CrownHolder(3));
    final int killerBefore = 0; // no crown

    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(victim, killer, (byte) 0));

    // Foreign-arena victim should not trigger our mechanic — its crown stays as-is.
    assertEquals("foreign-arena victim untouched",
        3, ed.getComponent(victim, CrownHolder.class).crowns());
    assertNull("killer not credited",
        ed.getComponent(killer, CrownHolder.class));
    // suppress unused
    assertEquals(0, killerBefore);
  }

  @Test
  public void onRoundEnd_clearsAllCrownsInArena() {
    final EntityId a = spawnPlayer(arenaId);
    final EntityId b = spawnPlayer(arenaId);
    final EntityId foreign = spawnPlayerInOtherArena();
    ed.setComponent(foreign, new CrownHolder(1));
    crowns.onRoundStart(arenaId, 1);

    crowns.onRoundEnd(arenaId, 1, null);

    assertNull(ed.getComponent(a, CrownHolder.class));
    assertNull(ed.getComponent(b, CrownHolder.class));
    assertNotNull("foreign arena's crown untouched",
        ed.getComponent(foreign, CrownHolder.class));
  }

  private EntityId spawnPlayer(final ArenaId arena) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, arena);
    ed.setComponent(ship, new PlayerShip());
    ed.setComponent(ship, new ShipType(Ship.WARBIRD));
    ed.setComponent(ship, new Frequency(0));
    return ship;
  }

  private EntityId spawnPlayerInOtherArena() {
    return spawnPlayer(new ArenaId("ffa", ed.createEntity()));
  }
}
