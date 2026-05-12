// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakActiveChange;
import org.junit.Test;

/**
 * Pins the ADR 0001 canonical-writer drain on {@link CloakSystem}. Value-replacement (boolean)
 * variant of {@link ThrustSystemChangeDrainTest}'s additive-delta shape — apply, no-op skip,
 * Decay-driven reverse to cached previous value. StealthSystem / XRadarSystem / AntiwarpSystem
 * follow the same shape; symmetry verified by inspection.
 */
public class CloakSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(CloakSystem.class, new CloakSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(final EntityData ed, final boolean active) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new CloakActive(active));
    return ship;
  }

  private static EntityId emit(final EntityData ed, final EntityId target, final boolean newValue) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new CloakActiveChange(newValue));
    return h;
  }

  @Test
  public void oneShotChange_flipsToggleAndReapsHolder() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, false);
      final EntityId h = emit(f.ed, ship, true);

      f.systems.update();

      assertTrue("Toggle flipped on", f.ed.getComponent(ship, CloakActive.class).isActive());
      assertNull(
          "One-shot holder destroyed by canonical writer",
          f.ed.getComponent(h, CloakActiveChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void emitMatchesCurrent_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, true);
      final EntityId h = emit(f.ed, ship, true);

      f.systems.update();

      assertTrue(f.ed.getComponent(ship, CloakActive.class).isActive());
      assertNull("Holder still destroyed on no-op", f.ed.getComponent(h, CloakActiveChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void temporaryChangeReversedOnRemove() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, false);
      final EntityId h = f.ed.createEntity();
      f.ed.setComponents(
          h, ChangeTarget.self(ship), new CloakActiveChange(true), new Decay(0L, 1_000_000L));

      f.systems.update();
      assertTrue("Temporary toggle applied", f.ed.getComponent(ship, CloakActive.class).isActive());

      f.ed.removeEntity(h);
      f.systems.update();

      assertFalse(
          "Decay-driven removal reverses to cached previous value (false)",
          f.ed.getComponent(ship, CloakActive.class).isActive());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void changeToSameValueWithDecay_reverseIsNoOp() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, true);
      final EntityId h = f.ed.createEntity();
      f.ed.setComponents(
          h, ChangeTarget.self(ship), new CloakActiveChange(true), new Decay(0L, 1_000_000L));

      f.systems.update();
      assertTrue(f.ed.getComponent(ship, CloakActive.class).isActive());

      f.ed.removeEntity(h);
      f.systems.update();

      assertTrue(
          "Cached previous value (true) preserved on reverse — no flip",
          f.ed.getComponent(ship, CloakActive.class).isActive());
    } finally {
      f.shutdown();
    }
  }

  private static final class Fixture {
    private final GameSystemManager systems;
    private final DefaultEntityData ed;

    private Fixture(final GameSystemManager systems, final DefaultEntityData ed) {
      this.systems = systems;
      this.ed = ed;
    }

    private void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
