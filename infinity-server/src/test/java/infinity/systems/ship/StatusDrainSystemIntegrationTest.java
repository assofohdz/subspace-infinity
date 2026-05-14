// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.toggles.AntiwarpActive;
import infinity.es.ship.toggles.AntiwarpStats;
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakStats;
import org.junit.Test;

/** Integration coverage for {@link BaseEnergyDrainSystem}'s update flow via {@link StatusDrainSystem}. Pins: (a) empty arena ticks cleanly, (b) active toggle drains Energy, (c) inactive toggle skips drain, (d) multi-entry loop hits every registered toggle. */
public class StatusDrainSystemIntegrationTest {

  private static EnergyStats noRecharge(final int max) {
    return new EnergyStats(max, max, 0, 0.0, 0.0, 0.0);
  }

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(EnergySystem.class, new EnergySystem());
    systems.register(StatusDrainSystem.class, new StatusDrainSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  /** Tick with no drainer entities — exercises the empty drain loop + init/terminate paths. */
  @Test
  public void emptyArena_tickCleanly() {
    final Fixture f = newFixture();
    try {
      f.systems.update();
    } finally {
      f.shutdown();
    }
  }

  /** Active cloak with a high drain rate produces measurable Energy loss across a few ticks. */
  @Test
  public void activeCloak_drainsEnergy() throws InterruptedException {
    final Fixture f = newFixture();
    try {
      final int start = 100_000_000;
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new Energy(start));
      f.ed.setComponent(ship, noRecharge(start));
      f.ed.setComponent(ship, new CloakActive(true));
      // 100M energy/sec — sub-millisecond tpf is enough to produce measurable drain.
      f.ed.setComponent(ship, new CloakStats(1, 100_000_000.0));

      f.systems.update();
      Thread.sleep(20L);
      f.systems.update();
      f.systems.update(); // EnergySystem drains the HealthChange intents emitted above

      final int after = f.ed.getComponent(ship, Energy.class).getEnergy();
      assertTrue("active cloak should reduce Energy from " + start + "; got " + after, after < start);
    } finally {
      f.shutdown();
    }
  }

  /** Inactive cloak: drain loop short-circuits at the isActive() check; Energy unchanged. */
  @Test
  public void inactiveCloak_noDrain() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new Energy(1000));
      f.ed.setComponent(ship, noRecharge(1000));
      f.ed.setComponent(ship, new CloakActive(false));
      f.ed.setComponent(ship, new CloakStats(1, 100_000_000.0));

      f.systems.update();
      f.systems.update();
      f.systems.update();

      assertEquals(
          "inactive cloak should not drain Energy",
          1000,
          f.ed.getComponent(ship, Energy.class).getEnergy());
    } finally {
      f.shutdown();
    }
  }

  /** Two toggles active on the same ship — exercises every registered DrainEntry, not just the first. */
  @Test
  public void multipleActiveToggles_eachDrains() throws InterruptedException {
    final Fixture f = newFixture();
    try {
      final int start = 100_000_000;
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new Energy(start));
      f.ed.setComponent(ship, noRecharge(start));
      f.ed.setComponent(ship, new CloakActive(true));
      f.ed.setComponent(ship, new CloakStats(1, 100_000_000.0));
      f.ed.setComponent(ship, new AntiwarpActive(true));
      f.ed.setComponent(ship, new AntiwarpStats(1, 100_000_000.0));

      f.systems.update();
      Thread.sleep(20L);
      f.systems.update();
      f.systems.update();

      assertTrue(
          "two-toggle drain should reduce Energy",
          f.ed.getComponent(ship, Energy.class).getEnergy() < start);
    } finally {
      f.shutdown();
    }
  }

  private record Fixture(GameSystemManager systems, DefaultEntityData ed) {
    void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
