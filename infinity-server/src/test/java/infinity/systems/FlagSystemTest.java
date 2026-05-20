// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.mathd.Grid;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.StaticBody;
import com.simsilica.sim.GameSystemManager;
import com.simsilica.sim.SimTime;
import infinity.es.Flag;
import infinity.es.FlagOwnership;
import infinity.es.Frequency;
import org.junit.Test;

/** Pins {@link FlagSystem}'s ship-touches-flag → {@code FlagOwnership(freq)} behaviour. */
public final class FlagSystemTest {

  private static final int GRID_SPACING = 1024;

  @Test
  public void shipTouchesUnownedFlag_writesFlagOwnership() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      final EntityId flag = f.ed.createEntity();
      f.ed.setComponent(ship, new Frequency(7));
      f.ed.setComponent(flag, new Flag());
      f.flagSystem.update(simTime(0));

      f.flagSystem.newContact(makeContact(ship, flag));

      final FlagOwnership owner = f.ed.getComponent(flag, FlagOwnership.class);
      assertNotNull("FlagSystem must write FlagOwnership on touch", owner);
      assertEquals(7, owner.freq());
    } finally {
      tearDown(f);
    }
  }

  @Test
  public void shipTouchesOwnedFlag_differentFreq_overwritesOwner() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      final EntityId flag = f.ed.createEntity();
      f.ed.setComponent(ship, new Frequency(2));
      f.ed.setComponent(flag, new Flag());
      f.ed.setComponent(flag, new FlagOwnership(1));
      f.flagSystem.update(simTime(0));

      f.flagSystem.newContact(makeContact(ship, flag));

      assertEquals(2, f.ed.getComponent(flag, FlagOwnership.class).freq());
    } finally {
      tearDown(f);
    }
  }

  @Test
  public void shipTouchesOwnFlag_sameFreq_isNoOp() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      final EntityId flag = f.ed.createEntity();
      f.ed.setComponent(ship, new Frequency(5));
      f.ed.setComponent(flag, new Flag());
      final FlagOwnership pre = new FlagOwnership(5);
      f.ed.setComponent(flag, pre);
      f.flagSystem.update(simTime(0));

      f.flagSystem.newContact(makeContact(ship, flag));

      // Component is value-equal but RaM rule #6 skips the setComponent call.
      assertEquals(5, f.ed.getComponent(flag, FlagOwnership.class).freq());
    } finally {
      tearDown(f);
    }
  }

  @Test
  public void contactWithNonFlagEntity_isIgnored() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      final EntityId nonFlag = f.ed.createEntity();
      f.ed.setComponent(ship, new Frequency(3));
      // nonFlag has no Flag component
      f.flagSystem.update(simTime(0));

      f.flagSystem.newContact(makeContact(ship, nonFlag));

      assertNull("non-flag entity must not receive FlagOwnership",
          f.ed.getComponent(nonFlag, FlagOwnership.class));
    } finally {
      tearDown(f);
    }
  }

  /** Builds a Contact with ship as body1 (RigidBody) and flag as body2 (StaticBody). */
  private static Contact<EntityId, MBlockShape> makeContact(final EntityId ship, final EntityId flag) {
    // No-arg ctor avoids the shape.getMass() dereference; set id by direct field write.
    final RigidBody<EntityId, MBlockShape> shipBody = new RigidBody<>();
    shipBody.id = ship;
    final StaticBody<EntityId, MBlockShape> flagBody = new StaticBody<>();
    flagBody.id = flag;
    flagBody.position.set(0, 0, 0);
    final Contact<EntityId, MBlockShape> contact = new Contact<>();
    contact.setBodies(shipBody, flagBody);
    return contact;
  }

  private static SimTime simTime(final long realTimeNanos) {
    final SimTime t = new SimTime();
    t.update(realTimeNanos);
    return t;
  }

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys = new PhysicsSpace<>(new Grid(GRID_SPACING));
    systems.register(EntityData.class, ed);
    systems.register(PhysicsSpace.class, phys);
    // Stub ContactSystem — FlagSystem only calls addListener/removeListener on it; the real
    // contact dispatch isn't needed because tests invoke flagSystem.newContact() directly.
    systems.register(ContactSystem.class, new StubContactSystem());
    final FlagSystem flagSystem = new FlagSystem();
    systems.register(FlagSystem.class, flagSystem);
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed, flagSystem);
  }

  /** Skips ContactSystem's MPhysSystem requirement; preserves addListener/removeListener. */
  private static final class StubContactSystem extends ContactSystem<EntityId, MBlockShape> {
    @Override
    protected void initialize() {
      // no-op — real ContactSystem requires MPhysSystem + ArenaSystem which the test doesn't.
    }

    @Override
    protected void terminate() {
      // no-op
    }
  }

  private static void tearDown(final Fixture f) {
    f.systems.stop();
    f.systems.terminate();
  }

  private static final class Fixture {
    final GameSystemManager systems;
    final EntityData ed;
    final FlagSystem flagSystem;

    Fixture(final GameSystemManager systems, final EntityData ed, final FlagSystem flagSystem) {
      this.systems = systems;
      this.ed = ed;
      this.flagSystem = flagSystem;
    }
  }
}
