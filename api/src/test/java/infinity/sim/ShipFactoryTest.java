// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import infinity.config.EngineConfig;
import infinity.es.Frequency;
import infinity.net.ShipTypeId;
import infinity.sim.specs.ShipArgs;
import org.junit.Test;

/** Spawn-time seed pillar — pins the player-ship default freq=0 + the AI-path no-seed contract. */
public class ShipFactoryTest {

  private static final int TEST_GRID_SPACING = 1024;

  @Test
  public void createPlayerShip_seedsFrequencyZero() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));
    final EntityId owner = ed.createEntity();

    final EntityId ship =
        ShipFactory.createPlayerShip(
            ed,
            new ShipArgs(
                new Vec3d(0, 0, 0),
                owner,
                phys,
                0L,
                ShipTypeId.WARBIRD.wireId(),
                EngineConfig.DEFAULTS.shipRadius()));

    final Frequency freq = ed.getComponent(ship, Frequency.class);
    assertNotNull("Player ship must carry a Frequency component at spawn", freq);
    assertEquals("Default spawn freq=0 (per user-confirmed default)", 0, freq.getFrequency());
  }

  @Test
  public void createShip_doesNotSeedFrequency() {
    // AIEntities.createMobShip relies on this — it calls createShip and then
    // stamps Frequency(1) itself to put mobs on a non-player team. If createShip
    // started seeding Frequency, mobs would briefly land on team 0 before the
    // AI-path overwrite, polluting freq-keyed EntitySets for one frame.
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));
    final EntityId owner = ed.createEntity();

    final EntityId ship =
        ShipFactory.createShip(
            ed,
            new ShipArgs(
                new Vec3d(0, 0, 0),
                owner,
                phys,
                0L,
                ShipTypeId.WARBIRD.wireId(),
                EngineConfig.DEFAULTS.shipRadius()));

    assertNull(
        "createShip must not seed Frequency — AI path stamps its own Frequency(1) afterwards",
        ed.getComponent(ship, Frequency.class));
  }
}
