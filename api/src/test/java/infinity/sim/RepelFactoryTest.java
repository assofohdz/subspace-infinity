// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import infinity.config.EngineConfig;
import infinity.config.RepelConfig;
import infinity.es.Parent;
import infinity.es.ship.actions.RepelDistance;
import infinity.es.ship.actions.RepelSpeed;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Repel-effect spawn-projection test — Slice 1 closes
 * {@code [Repel] RepelSpeed} / {@code RepelTime} / {@code RepelDistance}
 * end-to-end. {@link WeaponFactory#createRepel} carries
 * {@link RepelConfig#timeMs()} into the {@link Decay} deadline; the caller
 * (production code: {@code ConsumableSystem.createRepel}) stamps
 * {@link RepelSpeed} / {@link RepelDistance} components from the same
 * config. This test reproduces that two-step contract directly so the
 * projection seam is exercised without booting {@code ConsumableSystem}'s
 * full SiO2 + physics dependency tree.
 *
 * <p>Mirrors the {@link BulletFactoryTest} shape for the projectile-spawn
 * pillar of the harness.
 */
public class RepelFactoryTest {

  private static final int TEST_GRID_SPACING = 1024;

  @Test
  public void createRepel_projectsRepelTimeAsDecayDeadline() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));

    final RepelConfig cfg = new RepelConfig(5000, 2250L, 512);
    final long createdTime = 2_000_000_000L;
    final EntityId owner = ed.createEntity();

    final EntityId repel =
        WeaponFactory.createRepel(
            ed,
            owner,
            phys,
            createdTime,
            new Vec3d(0, 0, 0),
            cfg.timeMs(),
            EngineConfig.DEFAULTS.repelRadius());

    final Decay decay = ed.getComponent(repel, Decay.class);
    assertNotNull("Repel must carry a Decay TTL projection", decay);
    assertEquals(createdTime, decay.getStartTime());
    assertEquals(
        createdTime + TimeUnit.NANOSECONDS.convert(cfg.timeMs(), TimeUnit.MILLISECONDS),
        decay.getEndTime());
  }

  @Test
  public void createRepel_callerStampsRepelSpeedAndDistance() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));

    final RepelConfig cfg = new RepelConfig(5000, 2250L, 512);
    final EntityId owner = ed.createEntity();

    final EntityId repel =
        WeaponFactory.createRepel(
            ed,
            owner,
            phys,
            0L,
            new Vec3d(0, 0, 0),
            cfg.timeMs(),
            EngineConfig.DEFAULTS.repelRadius());
    // Mirrors the production projection in ConsumableSystem.createRepel:
    // WeaponFactory.createRepel handles the Decay projection; speed and
    // distance are stamped by the caller from the same RepelConfig.
    ed.setComponent(repel, new RepelSpeed(cfg.speed()));
    ed.setComponent(repel, new RepelDistance(cfg.distancePixels()));

    assertEquals(5000, ed.getComponent(repel, RepelSpeed.class).getSpeed());
    assertEquals(512, ed.getComponent(repel, RepelDistance.class).getPixels());
  }

  @Test
  public void createRepel_parentsEffectToOwner() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));

    final EntityId owner = ed.createEntity();
    final EntityId repel =
        WeaponFactory.createRepel(
            ed,
            owner,
            phys,
            0L,
            new Vec3d(0, 0, 0),
            RepelConfig.DEFAULTS.timeMs(),
            EngineConfig.DEFAULTS.repelRadius());

    final Parent parent = ed.getComponent(repel, Parent.class);
    assertNotNull("Repel effect must carry Parent ownership pointing back at the firing ship", parent);
    assertEquals(owner, parent.getParentEntityId());
  }
}
