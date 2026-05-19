// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.SimTime;
import infinity.Ship;
import infinity.es.Dead;
import infinity.es.Parent;
import infinity.es.lifecycle.CurrentShip;
import infinity.es.ship.PlayerShip;
import infinity.es.ship.ShipType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins {@link AvatarSystem}'s {@link CurrentShip} link lifecycle. Bypasses the heavier
 * {@code GameSystemManager} wiring (EngineConfigSystem + ArenaModuleSystem) via a thin
 * subclass that only seeds the EntitySets {@code syncCurrentShipLinks} actually uses.
 */
public final class AvatarSystemCurrentShipTest {

  private DefaultEntityData ed;
  private TestableAvatarSystem system;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    system = new TestableAvatarSystem(ed);
    system.initForTest();
  }

  @After
  public void tearDown() {
    system.terminateForTest();
  }

  @Test
  public void newPlayerShip_stampsCurrentShipOnParent() {
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player);

    tick();

    final CurrentShip link = ed.getComponent(player, CurrentShip.class);
    assertNotNull("CurrentShip stamped on player after ship-with-Parent appears", link);
    assertEquals(ship, link.getShipId());
  }

  @Test
  public void shipMarkedDead_removesCurrentShipFromParent() {
    final EntityId player = ed.createEntity();
    final EntityId ship = createPlayerShip(player);
    tick(); // stamp
    assertNotNull(ed.getComponent(player, CurrentShip.class));

    ed.setComponent(ship, new Dead(0L));
    tick();

    assertNull("CurrentShip removed when the linked ship goes Dead",
        ed.getComponent(player, CurrentShip.class));
  }

  @Test
  public void deadShipOfADifferentPlayer_doesNotClearMyLink() {
    final EntityId playerA = ed.createEntity();
    final EntityId shipA = createPlayerShip(playerA);
    final EntityId playerB = ed.createEntity();
    final EntityId shipB = createPlayerShip(playerB);
    tick();
    assertNotNull(ed.getComponent(playerA, CurrentShip.class));
    assertNotNull(ed.getComponent(playerB, CurrentShip.class));

    ed.setComponent(shipB, new Dead(0L));
    tick();

    assertNotNull("A's link unchanged when B's ship dies",
        ed.getComponent(playerA, CurrentShip.class));
    assertEquals(shipA, ed.getComponent(playerA, CurrentShip.class).getShipId());
    assertNull(ed.getComponent(playerB, CurrentShip.class));
  }

  /** Bot ships are stripped of {@code Player} by {@code AIEntities.createMobShip}; verify the link doesn't fire. */
  @Test
  public void shipWithoutPlayerMarker_doesNotStampLink() {
    final EntityId fakeParent = ed.createEntity();
    final EntityId bot = ed.createEntity();
    ed.setComponent(bot, new Parent(fakeParent));
    ed.setComponent(bot, new ShipType(Ship.JAVELIN));
    // no Player marker — bot pattern

    tick();

    assertNull(ed.getComponent(fakeParent, CurrentShip.class));
  }

  private EntityId createPlayerShip(final EntityId player) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new PlayerShip());
    ed.setComponent(ship, new Parent(player));
    ed.setComponent(ship, new ShipType(Ship.WARBIRD));
    return ship;
  }

  private void tick() {
    system.update(simTimeAt(0L));
  }

  private static SimTime simTimeAt(final long nanos) {
    final SimTime t = new SimTime();
    t.update(nanos);
    return t;
  }

  /**
   * Test seam: skips {@code requireSystem} dependencies AvatarSystem normally needs
   * for {@code requestShipChange} (EngineConfigSystem + ArenaModuleSystem) and only
   * wires the EntitySets the link-maintenance path reads.
   */
  private static final class TestableAvatarSystem extends AvatarSystem {

    TestableAvatarSystem(final EntityData ed) {
      super();
      this.ed = ed;
    }

    void initForTest() {
      this.playerShips = ed.getEntities(PlayerShip.class, Parent.class);
      this.deadPlayerShips = ed.getEntities(PlayerShip.class, Parent.class, Dead.class);
    }

    void terminateForTest() {
      this.playerShips.release();
      this.deadPlayerShips.release();
    }

    @Override
    public void update(final SimTime tpf) {
      // Only run the link-maintenance phase — skip the shipType / captain drains
      // since their EntitySets weren't initialized.
      syncCurrentShipLinks();
    }
  }
}
