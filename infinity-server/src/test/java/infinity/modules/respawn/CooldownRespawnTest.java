// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.respawn;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.Ship;
import infinity.config.CooldownRespawnConfig;
import infinity.es.Frequency;
import infinity.es.Parent;
import infinity.es.arena.ArenaId;
import infinity.es.ship.PlayerShip;
import infinity.es.ship.ShipType;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins {@link CooldownRespawn}'s capture-on-event + drain-after-delay state machine. Uses a
 * subclass test seam to record spawn requests without invoking the physics-bound
 * {@code ShipFactory.createPlayerShip} (mirrors {@code FillUpXTeamsTest}'s pattern).
 */
public final class CooldownRespawnTest {

  private DefaultEntityData ed;
  private ArenaId thisArena;
  private ArenaId otherArena;
  private RecordingCooldownRespawn module;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    thisArena = new ArenaId("ffa", ed.createEntity());
    otherArena = new ArenaId("trench", ed.createEntity());
    module = new RecordingCooldownRespawn(
        new ModuleContext(thisArena, ed.createEntity(), ed, null, null),
        new CooldownRespawnConfig(5));
    module.onArenaLoad(thisArena);
  }

  @After
  public void tearDown() {
    module.onArenaUnload(thisArena);
  }

  @Test
  public void killEnqueuesAndStaysPendingBelowCooldown() {
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, thisArena, Ship.JAVELIN);
    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(10)));

    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(ship, null, (byte) 0));
    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(11)));
    assertEquals("still under cooldown — no respawn yet", 0, module.respawns.size());

    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(14)));
    assertEquals("4s elapsed, threshold 5 — still pending", 0, module.respawns.size());

    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(15)));
    assertEquals("5s elapsed — respawn fires", 1, module.respawns.size());
    final RecordedRespawn r = module.respawns.get(0);
    assertEquals(player, r.player());
    assertEquals(Ship.JAVELIN, Ship.getShip(r.shipByte()));
  }

  @Test
  public void killCarriesFrequencyIntoRespawn() {
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, thisArena, Ship.SPIDER);
    ed.setComponent(ship, new Frequency(7));
    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(0)));
    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(ship, null, (byte) 0));

    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(5)));
    assertEquals(1, module.respawns.size());
    assertEquals(7, module.respawns.get(0).freq());
  }

  @Test
  public void foreignArenaKillIsIgnored() {
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, otherArena, Ship.JAVELIN);
    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(0)));
    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(ship, null, (byte) 0));

    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(100)));
    assertEquals("foreign-arena kill ignored", 0, module.respawns.size());
  }

  /** Bots carry the {@code BotShip} marker; their respawn is the FillUpXTeams mechanic's job. */
  @Test
  public void botKillIsIgnored() {
    final EntityId fakeParent = ed.createEntity();
    final EntityId bot = ed.createEntity();
    ed.setComponent(bot, thisArena);
    ed.setComponent(bot, new Parent(fakeParent));
    ed.setComponent(bot, new ShipType(Ship.JAVELIN));
    ed.setComponent(bot, new infinity.es.ship.BotShip());
    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(0)));
    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(bot, null, (byte) 0));

    module.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(100)));
    assertEquals(0, module.respawns.size());
  }

  @Test
  public void zeroSecondCooldownRespawnsNextTick() {
    final RecordingCooldownRespawn instant = new RecordingCooldownRespawn(
        new ModuleContext(thisArena, ed.createEntity(), ed, null, null),
        new CooldownRespawnConfig(0));
    instant.onArenaLoad(thisArena);

    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, thisArena, Ship.WARBIRD);
    instant.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(0)));
    EventBus.publish(PlayerKilledEvent.playerKilled, new PlayerKilledEvent(ship, null, (byte) 0));

    instant.tickRespawnPolicy(thisArena, simTimeAt(secondsToNanos(0)));
    assertEquals("seconds=0 is degenerate-instant; respawn fires same/next tick", 1,
        instant.respawns.size());

    instant.onArenaUnload(thisArena);
  }

  private EntityId createPlayerShip(final EntityId player, final ArenaId arena, final Ship ship) {
    final EntityId shipEntity = ed.createEntity();
    ed.setComponent(shipEntity, arena);
    ed.setComponent(shipEntity, new Parent(player));
    ed.setComponent(shipEntity, new ShipType(ship));
    ed.setComponent(shipEntity, new PlayerShip());
    return shipEntity;
  }

  private static long secondsToNanos(final long s) {
    return TimeUnit.SECONDS.toNanos(s);
  }

  private static SimTime simTimeAt(final long nanos) {
    final SimTime t = new SimTime();
    // SimTime.update() computes delta-from-last; first call always yields delta=0. Force the
    // absolute game-time directly so getTime() returns the requested value immediately.
    t.setCurrentTime(nanos);
    return t;
  }

  private record RecordedRespawn(EntityId player, byte shipByte, int freq) {}

  /** Records every spawnShip call instead of creating a real ship via the physics-bound factory. */
  private static final class RecordingCooldownRespawn extends CooldownRespawn {
    final List<RecordedRespawn> respawns = new ArrayList<>();

    RecordingCooldownRespawn(final ModuleContext ctx, final CooldownRespawnConfig cfg) {
      super(ctx, cfg);
    }

    @Override
    protected Vec3d resolveSpawn(final int freq) {
      return new Vec3d(0, 0, 0); // any non-null
    }

    @Override
    protected EntityId spawnShip(
        final EntityId player,
        final byte shipByte,
        final int freq,
        final Vec3d spawnLoc,
        final long createdTimeNanos) {
      respawns.add(new RecordedRespawn(player, shipByte, freq));
      return null;
    }
  }
}
