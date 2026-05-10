// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import infinity.config.EngineConfig;
import infinity.es.Hidden;
import infinity.es.PrizeTypes;
import infinity.es.Spawner;
import infinity.sim.specs.PrizeSpec;
import infinity.sim.specs.SpawnerCreateSpec;
import java.util.Map;
import org.junit.Test;

/**
 * Factory pillar of the spawn-projection test harness for Slice 8d (C2).
 * Exercises the {@code MapFactory.createSpawner} + {@code createPrize}
 * factories at the api-side seam: a synthetic {@link DefaultEntityData} +
 * single-cell {@link PhysicsSpace} suffice; no system manager, no physics
 * step.
 *
 * <p>Mirrors the contract pattern from {@link BulletFactoryTest}. Behavioural
 * coverage of {@code PrizeSystem.update}'s additive scaling + regen-batch
 * loop sits behind the broader spawn-projection harness backlog (full
 * {@code PrizeSystem} fixture is heavy — 4–5 dependent systems). Manual
 * smoke + the factory assertions below cover the projection contract until
 * that harness lands.
 */
public class SpawnerProjectionTest {

  private static final int TEST_GRID_SPACING = 1024;

  private static PhysicsSpace<EntityId, MBlockShape> newPhys() {
    return new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));
  }

  @Test
  public void createSpawner_defaults_carryNoOpScaling() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys = newPhys();

    // Defaults match the pre-spec convenience overload: PRIZE_DEFAULT_MAX_COUNT,
    // no per-spawner ttl, no weight overrides, zero per-player scaling, regen=1, visible.
    final EntityId spawnerId =
        MapFactory.createSpawner(
            ed,
            new SpawnerCreateSpec(
                EntityId.NULL_ID,
                phys,
                0L,
                new Vec3d(0, 0, 0),
                5000.0,
                false,
                100.0,
                MapFactory.PRIZE_DEFAULT_MAX_COUNT,
                0L,
                Map.of(),
                0,
                0.0,
                1,
                false));

    final Spawner s = ed.getComponent(spawnerId, Spawner.class);
    assertNotNull("createSpawner must stamp a Spawner component", s);
    assertEquals("default countPerPlayer collapses scaling", 0, s.getCountPerPlayer());
    assertEquals("default radiusPerPlayer collapses scaling", 0.0, s.getRadiusPerPlayer(), 0.0);
    assertEquals("default regenBatch preserves pre-Slice-8d cadence", 1, s.getRegenBatch());
    assertFalse("defaults visible (not hidden)", s.isHidden());
  }

  @Test
  public void createSpawner_explicit_carriesNewFields() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys = newPhys();

    final EntityId spawnerId =
        MapFactory.createSpawner(
            ed,
            new SpawnerCreateSpec(
                EntityId.NULL_ID,
                phys,
                0L,
                new Vec3d(0, 0, 0),
                1500.0,
                false,
                400.0,
                5,
                10000L,
                Map.of(),
                2,
                50.0,
                3,
                true));

    final Spawner s = ed.getComponent(spawnerId, Spawner.class);
    assertNotNull(s);
    assertEquals(2, s.getCountPerPlayer());
    assertEquals(50.0, s.getRadiusPerPlayer(), 0.0);
    assertEquals(3, s.getRegenBatch());
    assertTrue(s.isHidden());
  }

  @Test
  public void createPrize_hiddenTrue_stampsHidden() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys = newPhys();

    final EntityId prizeId =
        MapFactory.createPrize(
            ed,
            new PrizeSpec(
                phys,
                0L,
                new Vec3d(0, 0, 0),
                PrizeTypes.GUN,
                5000L,
                true,
                EngineConfig.DEFAULTS.prizeRadius()));

    assertNotNull(
        "createPrize(hidden=true) must stamp a Hidden marker",
        ed.getComponent(prizeId, Hidden.class));
  }

  @Test
  public void createPrize_hiddenFalse_doesNotStampHidden() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys = newPhys();

    final EntityId prizeId =
        MapFactory.createPrize(
            ed,
            new PrizeSpec(
                phys,
                0L,
                new Vec3d(0, 0, 0),
                PrizeTypes.GUN,
                5000L,
                false,
                EngineConfig.DEFAULTS.prizeRadius()));

    assertNull(
        "createPrize(hidden=false) must not stamp a Hidden marker",
        ed.getComponent(prizeId, Hidden.class));
  }
}
