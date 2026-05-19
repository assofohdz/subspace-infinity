// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import infinity.config.PortalConfig;
import infinity.es.Parent;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Portal spawn-projection test — Slice 5 closes
 * {@code [Misc] WarpPointDelay} end-to-end (plumbing only). The sibling
 * {@code [Misc] WarpRadiusLimit} (Spawn-mechanic per REFERENCE.md) is
 * now owned by {@code RandomRadiusSpawnPlacement} as the {@code radius}
 * kwarg.
 *
 * <p>{@link MapFactory#createPortal} composes a marker entity carrying
 * {@link PortalConfig#activeTimeMs()} as a {@link Decay} deadline. The
 * caller (production: {@code ConsumableSystem.createPortal}) decrements
 * the ship's {@code Portal} inventory before invoking the factory; this
 * test exercises the factory contract directly so the projection seam
 * is verified without booting the full SiO2 dependency tree.
 *
 * <p>Mirrors the {@link DecoyFactoryTest} shape for the marker-entity
 * pillar of the harness.
 */
public class PortalFactoryTest {

  @Test
  public void createPortal_projectsActiveTimeAsDecayDeadline() {
    final DefaultEntityData ed = new DefaultEntityData();

    final PortalConfig cfg = new PortalConfig(/* activeTimeMs */ 240_000L);
    final long createdTime = 2_000_000_000L;
    final EntityId owner = ed.createEntity();

    final EntityId portal =
        MapFactory.createPortal(ed, owner, createdTime, cfg.activeTimeMs());

    assertNotNull("createPortal must return a real entity", portal);

    // Parent links the marker to the placing ship.
    final Parent parent = ed.getComponent(portal, Parent.class);
    assertNotNull("portal must carry a Parent component", parent);
    assertEquals(
        "Parent.entityId must point at the placing ship",
        owner,
        parent.getParentEntityId());

    // Decay deadline = createdTime + (WarpPointDelay ms in ns). The
    // canonical decay reaper deletes the entity at the deadline.
    final Decay decay = ed.getComponent(portal, Decay.class);
    assertNotNull("portal must carry a Decay component", decay);
    assertEquals(
        "Decay deadline must equal createdTime + WarpPointDelay (ms→ns)",
        createdTime + TimeUnit.NANOSECONDS.convert(cfg.activeTimeMs(), TimeUnit.MILLISECONDS),
        decay.getEndTime());
  }
}
