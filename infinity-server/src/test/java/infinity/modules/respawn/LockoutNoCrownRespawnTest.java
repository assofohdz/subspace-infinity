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
import infinity.es.CrownHolder;
import infinity.es.Parent;
import infinity.es.arena.ArenaId;
import infinity.es.ship.PlayerShip;
import infinity.es.ship.ShipType;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins {@link LockoutNoCrownRespawn}: pending respawns stay gated until at least one
 * {@link CrownHolder} exists in the arena, then drain. {@code onRoundEnd} force-drains.
 */
public final class LockoutNoCrownRespawnTest {

  private DefaultEntityData ed;
  private ArenaId arenaId;
  private RecordingLockoutRespawn module;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    arenaId = new ArenaId("koth", ed.createEntity());
    module = new RecordingLockoutRespawn(
        new ModuleContext(arenaId, ed.createEntity(), ed, null, null));
    module.onArenaLoad(arenaId);
  }

  @After
  public void tearDown() {
    module.onArenaUnload(arenaId);
  }

  @Test
  public void killWithCrownsAlive_respawnsImmediately() {
    final EntityId holder = ed.createEntity();
    ed.setComponent(holder, arenaId);
    ed.setComponent(holder, new CrownHolder(1));
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, arenaId);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(ship, null, (byte) 0));
    module.tickRespawnPolicy(arenaId, simTimeAt(0));

    assertEquals(1, module.respawns.size());
    assertEquals(player, module.respawns.get(0).player());
  }

  @Test
  public void killWithNoCrownsLeft_stayBlockedUntilCrownReappears() {
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, arenaId);
    // No CrownHolder anywhere.

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(ship, null, (byte) 0));
    module.tickRespawnPolicy(arenaId, simTimeAt(0));
    assertEquals("locked out", 0, module.respawns.size());

    // Sim time progresses; still no crowns → still blocked.
    module.tickRespawnPolicy(arenaId, simTimeAt(60_000_000_000L));
    assertEquals("still locked out 60s later", 0, module.respawns.size());

    // A crown appears (e.g., a new player joined and got a crown on next roundStart).
    final EntityId holder = ed.createEntity();
    ed.setComponent(holder, arenaId);
    ed.setComponent(holder, new CrownHolder(1));
    module.tickRespawnPolicy(arenaId, simTimeAt(60_000_000_001L));

    assertEquals("drains once a crown is back", 1, module.respawns.size());
  }

  @Test
  public void onRoundEnd_forceDrainsEvenWithoutCrowns() {
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, arenaId);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(ship, null, (byte) 0));
    module.tickRespawnPolicy(arenaId, simTimeAt(0));
    assertEquals("locked out (no crowns)", 0, module.respawns.size());

    module.onRoundEnd(arenaId, 1, null);

    assertEquals("round-end force-drains the queue", 1, module.respawns.size());
  }

  @Test
  public void foreignArenaKill_isIgnored() {
    final ArenaId other = new ArenaId("ffa", ed.createEntity());
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, other);

    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(ship, null, (byte) 0));
    module.tickRespawnPolicy(arenaId, simTimeAt(0));
    module.onRoundEnd(arenaId, 1, null);

    assertEquals(0, module.respawns.size());
  }

  @Test
  public void crownInDifferentArena_doesNotOpenGate() {
    final ArenaId other = new ArenaId("ffa", ed.createEntity());
    final EntityId foreignHolder = ed.createEntity();
    ed.setComponent(foreignHolder, other);
    ed.setComponent(foreignHolder, new CrownHolder(1));

    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player, arenaId);
    EventBus.publish(PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(ship, null, (byte) 0));
    module.tickRespawnPolicy(arenaId, simTimeAt(0));

    assertEquals("foreign-arena crown doesn't open this arena's gate",
        0, module.respawns.size());
  }

  private EntityId createPlayerShip(final EntityId player, final ArenaId arena) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, arena);
    ed.setComponent(ship, new Parent(player));
    ed.setComponent(ship, new ShipType(Ship.WARBIRD));
    ed.setComponent(ship, new PlayerShip());
    return ship;
  }

  private static SimTime simTimeAt(final long nanos) {
    final SimTime t = new SimTime();
    t.setCurrentTime(nanos);
    return t;
  }

  private record RecordedRespawn(EntityId player, byte shipByte, int freq) {}

  private static final class RecordingLockoutRespawn extends LockoutNoCrownRespawn {
    final List<RecordedRespawn> respawns = new ArrayList<>();

    RecordingLockoutRespawn(final ModuleContext ctx) {
      super(ctx);
    }

    @Override
    protected Vec3d resolveSpawn(final int freq) {
      return new Vec3d(0, 0, 0);
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
