/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelMax;
import org.junit.Test;

/**
 * Prize-pickup pillar of the spawn-projection test harness — proves the
 * harness extends from spawn-time projection (covered in
 * {@link infinity.systems.ShipSpawnSystemTest}) to runtime component
 * mutations performed by {@code PrizeApplier}s.
 *
 * <p>Same fixture pattern: a bare {@link DefaultEntityData} + a synthetic
 * ship entity. No {@code GameSystemManager} needed — appliers are plain
 * {@link PrizeApplier} instances that read and write components through
 * their {@link PrizeApplierContext}. Future Count-family applier tests
 * (Rocket, Brick, Decoy, Burst) follow this shape verbatim; the
 * Status-family appliers will need an {@code EnergySystem} stub once
 * Slice 6 lands. See {@code .scratch/spawn-projection-test-harness/PRD.md}.
 */
public class RepelPrizeApplierTest {

  /**
   * Below the cap → count increments by one.
   */
  @Test
  public void apply_belowCap_incrementsRepelCount() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new RepelMax(20));
    ed.setComponent(ship, new Repel(10));

    final PrizeApplierContext ctx = new PrizeApplierContext(ed, null, null);
    new RepelPrizeApplier().apply(ship, ctx);

    assertEquals(11, ed.getComponent(ship, Repel.class).getCount());
    assertEquals(20, ed.getComponent(ship, RepelMax.class).getCount());
  }

  /**
   * At the cap → count is unchanged. Consumes the prize silently — Subspace
   * canonical behaviour.
   */
  @Test
  public void apply_atCap_leavesCountUnchanged() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new RepelMax(20));
    ed.setComponent(ship, new Repel(20));

    final PrizeApplierContext ctx = new PrizeApplierContext(ed, null, null);
    new RepelPrizeApplier().apply(ship, ctx);

    assertEquals(20, ed.getComponent(ship, Repel.class).getCount());
  }

  /**
   * Ship without {@link RepelMax} = repels disallowed for this ship type
   * (Subspace per-ship {@code MaxRepels=0}). Applier no-ops; no
   * {@link Repel} component is conjured.
   */
  @Test
  public void apply_repelsDisallowed_noComponentsWritten() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    final PrizeApplierContext ctx = new PrizeApplierContext(ed, null, null);
    new RepelPrizeApplier().apply(ship, ctx);

    assertNull(ed.getComponent(ship, Repel.class));
    assertNull(ed.getComponent(ship, RepelMax.class));
  }
}
