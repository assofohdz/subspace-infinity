// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import infinity.config.DecoyConfig;
import infinity.es.Parent;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Decoy spawn-projection test — Slice 4 closes
 * {@code [Misc] DecoyAliveTime} end-to-end (plumbing only).
 * {@link MapFactory#createDecoy} composes a marker entity carrying
 * {@link DecoyConfig#aliveTimeMs()} as a {@link Decay} deadline. The
 * caller (production: {@code ConsumableSystem.createDecoy}) decrements
 * the ship's {@code Decoy} inventory before invoking the factory; this
 * test exercises the factory contract directly so the projection seam
 * is verified without booting the full SiO2 dependency tree.
 *
 * <p>Mirrors the {@link BrickFactoryTest} shape for the marker-entity
 * pillar of the harness. The follow-up "decoy as radar fake" slice will
 * extend this test with shape / radar-visibility / heading-mimic
 * assertions once those land.
 */
public class DecoyFactoryTest {

  @Test
  public void createDecoy_projectsAliveTimeAsDecayDeadline() {
    final DefaultEntityData ed = new DefaultEntityData();

    final DecoyConfig cfg = new DecoyConfig(/* aliveTimeMs */ 100_000L);
    final long createdTime = 2_000_000_000L;
    final EntityId owner = ed.createEntity();

    final EntityId decoy =
        MapFactory.createDecoy(ed, owner, createdTime, cfg.aliveTimeMs());

    assertNotNull("createDecoy must return a real entity", decoy);

    // Parent links the marker to the placing ship.
    final Parent parent = ed.getComponent(decoy, Parent.class);
    assertNotNull("decoy must carry a Parent component", parent);
    assertEquals(
        "Parent.entityId must point at the placing ship",
        owner,
        parent.getParentEntityId());

    // Decay deadline = createdTime + (DecoyAliveTime ms in ns). The
    // canonical decay reaper deletes the entity at the deadline.
    final Decay decay = ed.getComponent(decoy, Decay.class);
    assertNotNull("decoy must carry a Decay component", decay);
    assertEquals(
        "Decay deadline must equal createdTime + DecoyAliveTime (ms→ns)",
        createdTime + TimeUnit.NANOSECONDS.convert(cfg.aliveTimeMs(), TimeUnit.MILLISECONDS),
        decay.getEndTime());
  }
}
