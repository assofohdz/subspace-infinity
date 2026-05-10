// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import infinity.config.BrickConfig;
import infinity.es.Parent;
import infinity.es.ship.actions.BrickSpan;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Brick spawn-projection test — Slice 3 closes {@code [Brick] BrickSpan}
 * / {@code BrickTime} end-to-end (plumbing only).
 * {@link MapFactory#createBrick} composes a marker entity carrying
 * {@link BrickConfig#spanTiles()} on a {@link BrickSpan} component plus
 * {@link BrickConfig#timeMs()} as a {@link Decay} deadline. The caller
 * (production: {@code ConsumableSystem.createBrick}) decrements the
 * ship's {@code Brick} inventory before invoking the factory; this test
 * exercises the factory contract directly so the projection seam is
 * verified without booting the full SiO2 dependency tree.
 *
 * <p>Mirrors the {@link RepelFactoryTest} shape for the marker-entity
 * pillar of the harness. The follow-up "make bricks solid" slice will
 * extend this test with shape / collision-filter / per-tile geometry
 * assertions once those land.
 */
public class BrickFactoryTest {

  @Test
  public void createBrick_carriesSpanAndProjectsBrickTimeAsDecayDeadline() {
    final DefaultEntityData ed = new DefaultEntityData();

    final BrickConfig cfg = new BrickConfig(/* spanTiles */ 7, /* timeMs */ 10_000L);
    final long createdTime = 2_000_000_000L;
    final EntityId owner = ed.createEntity();

    final EntityId brick =
        MapFactory.createBrick(ed, owner, createdTime, cfg.spanTiles(), cfg.timeMs());

    assertNotNull("createBrick must return a real entity", brick);

    // Parent links the marker to the placing ship.
    final Parent parent = ed.getComponent(brick, Parent.class);
    assertNotNull("brick must carry a Parent component", parent);
    assertEquals(
        "Parent.entityId must point at the placing ship",
        owner,
        parent.getParentEntityId());

    // BrickSpan carries the wall length forward to the (deferred) brick-
    // geometry slice.
    final BrickSpan span = ed.getComponent(brick, BrickSpan.class);
    assertNotNull("brick must carry a BrickSpan component", span);
    assertEquals("BrickSpan must equal config.spanTiles", 7, span.getTiles());

    // Decay deadline = createdTime + (BrickTime ms in ns). The canonical
    // decay reaper deletes the entity at the deadline.
    final Decay decay = ed.getComponent(brick, Decay.class);
    assertNotNull("brick must carry a Decay component", decay);
    assertEquals(
        "Decay deadline must equal createdTime + BrickTime (ms→ns)",
        createdTime + TimeUnit.NANOSECONDS.convert(cfg.timeMs(), TimeUnit.MILLISECONDS),
        decay.getEndTime());
  }
}
