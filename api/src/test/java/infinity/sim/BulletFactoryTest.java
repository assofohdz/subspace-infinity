// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import infinity.config.BulletConfig;
import infinity.config.EngineConfig;
import infinity.es.Parent;
import infinity.es.ShapeNames;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Projectile-spawn pillar of the spawn-projection test harness — proves the
 * harness extends to the {@code WeaponFactory.create*} factory layer that
 * stamps the per-arena {@link BulletConfig} (decay, damage tier) onto a
 * fresh projectile entity.
 *
 * <p>Stays scoped to the api-side factory contract:
 * {@link WeaponFactory#createBullet} composes structural components on a
 * synthetic {@link DefaultEntityData}; a real {@link PhysicsSpace} backed
 * by a single-cell {@link Grid} provides the {@code getGrid()} call the
 * factory needs for {@link SpawnPosition}. No {@code WeaponsSystem}, no
 * physics step, no SiO2 system manager. Future projectile slices (Slice 10
 * — per-ship {@code BulletSpeed} / {@code BombSpeed}) extend this fixture
 * to assert the projection of those config fields onto the spawned entity.
 *
 * <p>See {@code .scratch/spawn-projection-test-harness/PRD.md}.
 */
public class BulletFactoryTest {

  private static final int TEST_GRID_SPACING = 1024;

  @Test
  public void createBullet_projectsDecayMsAsDecayDeadline() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));

    final BulletConfig cfg = new BulletConfig(100, 50, 5500L);
    final long createdTime = 1_000_000_000L;
    final EntityId owner = ed.createEntity();

    final EntityId bullet =
        WeaponFactory.createBullet(
            ed,
            owner,
            phys,
            createdTime,
            new Vec3d(0, 0, 0),
            new Vec3d(0, 0, 50),
            cfg.decayMs(),
            ShapeNames.BULLETL1,
            EngineConfig.DEFAULTS.bulletRadius());

    final Decay decay = ed.getComponent(bullet, Decay.class);
    assertNotNull("Bullet must carry a Decay TTL projection", decay);
    assertEquals(createdTime, decay.getStartTime());
    assertEquals(
        createdTime + TimeUnit.NANOSECONDS.convert(cfg.decayMs(), TimeUnit.MILLISECONDS),
        decay.getEndTime());
  }

  /**
   * Per-level damage projection (per Subspace {@code damageAtLevel = damage
   * + (level - 1) × damageUpgrade}) is the contract {@code WeaponsSystem}
   * relies on when stamping {@code Damage} on the spawned bullet. Projection
   * formula belongs to {@link BulletConfig} so the test sits at the seam
   * the factory layer exposes — no system manager required.
   */
  @Test
  public void bulletConfig_damageAtLevel_followsSubspaceFormula() {
    final BulletConfig cfg = new BulletConfig(100, 50, 5500L);
    assertEquals(100, cfg.damageAtLevel(1));
    assertEquals(150, cfg.damageAtLevel(2));
    assertEquals(200, cfg.damageAtLevel(3));
    assertEquals(250, cfg.damageAtLevel(4));
  }

  @Test
  public void createBullet_parentsBulletToOwner() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));

    final EntityId owner = ed.createEntity();
    final EntityId bullet =
        WeaponFactory.createBullet(
            ed,
            owner,
            phys,
            0L,
            new Vec3d(0, 0, 0),
            new Vec3d(0, 0, 50),
            5500L,
            ShapeNames.BULLETL1,
            EngineConfig.DEFAULTS.bulletRadius());

    final Parent parent = ed.getComponent(bullet, Parent.class);
    assertNotNull("Bullet must carry Parent ownership pointing back at the firing ship", parent);
    assertEquals(owner, parent.getParentEntityId());
  }
}
