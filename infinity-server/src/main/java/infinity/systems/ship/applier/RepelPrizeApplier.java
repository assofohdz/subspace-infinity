// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INVENTORY family.</b> Bumps the ship's {@link Repel} count by one toward
 * {@link RepelMax}. No-op when the ship has no {@link RepelMax} component
 * (= ship not allowed repels) or is already at the cap.
 *
 * <p>Subspace canonical knobs (REFERENCE.md {@code ## Repel} line 250):
 * <ul>
 *   <li>{@code RepelSpeed} — repulsion speed (Subspace velocity units)
 *   <li>{@code RepelTime} — affected duration (centiseconds)
 *   <li>{@code RepelDistance} — affected radius (pixels)
 * </ul>
 * Per-ship inventory caps: {@code [Ship] InitialRepel} / {@code RepelMax}
 * (REFERENCE.md "Inventory caps and starts" line 372/374).
 * See {@code ## PrizeWeight} line 242.
 *
 * <p>Pure component read/write — no {@link infinity.config.ShipConfig}
 * access on the hot path. Applier only adjusts inventory; the canon knobs
 * above describe what happens when the repel is fired and belong to
 * {@code ConsumableSystem}, not here.
 */
public final class RepelPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RepelPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RepelMax max = ed.getComponent(ship, RepelMax.class);
    if (max == null) {
      return; // ship not allowed repels
    }
    final Repel curr = ed.getComponent(ship, Repel.class);
    if (curr == null) {
      log.warn(
          "Ship {} has RepelMax but no Repel — spawn projection invariant broken; skipping repel prize",
          ship);
      return;
    }
    if (curr.getCount() < max.getCount()) {
      final int next = curr.getCount() + 1;
      log.info("Ship {} picked up repel prize and now has {} repels", ship, next);
      ed.setComponent(ship, new Repel(next));
    }
  }
}
