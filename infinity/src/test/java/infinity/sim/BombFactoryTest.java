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
import infinity.config.BombConfig;
import infinity.es.Parent;
import infinity.es.ShapeNames;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Projectile-spawn pillar of the spawn-projection test harness for bombs.
 * Exercises the api-side {@link GameEntities#createBomb} factory contract
 * (decay TTL projection, parent ownership). The slice-9a per-level splash
 * radius arithmetic and FF gate are package-private helpers on
 * {@code WeaponsSystem}, so their tests live in
 * {@code infinity.systems.ship.WeaponsSystemSplashTest}.
 */
public class BombFactoryTest {

  private static final int TEST_GRID_SPACING = 1024;

  @Test
  public void createBomb_projectsDecayMsAsDecayDeadline() {
    final DefaultEntityData ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));

    final BombConfig cfg = new BombConfig(2650, 60_000L, 5.0, 0, 0L, false, 0L, true);
    final long createdTime = 1_000_000_000L;
    final EntityId owner = ed.createEntity();

    final EntityId bomb =
        GameEntities.createBomb(
            ed,
            owner,
            phys,
            createdTime,
            new Vec3d(0, 0, 0),
            new Vec3d(0, 0, 25),
            cfg.decayMs(),
            ShapeNames.BOMBL1);

    final Decay decay = ed.getComponent(bomb, Decay.class);
    assertNotNull("Bomb must carry a Decay TTL projection", decay);
    assertEquals(createdTime, decay.getStartTime());
    assertEquals(
        createdTime + TimeUnit.NANOSECONDS.convert(cfg.decayMs(), TimeUnit.MILLISECONDS),
        decay.getEndTime());

    final Parent parent = ed.getComponent(bomb, Parent.class);
    assertNotNull("Bomb must carry Parent ownership", parent);
    assertEquals(owner, parent.getParentEntityId());
  }
}
