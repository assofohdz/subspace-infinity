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
import infinity.es.ship.toggles.XRadarActive;
import infinity.es.ship.toggles.XRadarActiveChange;
import org.junit.Test;

/** Minimal drain pin for {@link XRadarSystem} — same shape as {@link CloakSystemChangeDrainTest}. */
public class XRadarSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(XRadarSystem.class, new XRadarSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  @Test
  public void oneShotChange_flipsToggleAndReapsHolder() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new XRadarActive(false));
      final EntityId h = f.ed.createEntity();
      f.ed.setComponents(h, ChangeTarget.self(ship), new XRadarActiveChange(true));

      f.systems.update();

      assertTrue(f.ed.getComponent(ship, XRadarActive.class).isActive());
      assertNull(f.ed.getComponent(h, XRadarActiveChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void temporaryChangeReversedOnRemove() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new XRadarActive(false));
      final EntityId h = f.ed.createEntity();
      f.ed.setComponents(
          h, ChangeTarget.self(ship), new XRadarActiveChange(true), new Decay(0L, 1_000_000L));

      f.systems.update();
      assertTrue(f.ed.getComponent(ship, XRadarActive.class).isActive());

      f.ed.removeEntity(h);
      f.systems.update();

      assertFalse(f.ed.getComponent(ship, XRadarActive.class).isActive());
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
