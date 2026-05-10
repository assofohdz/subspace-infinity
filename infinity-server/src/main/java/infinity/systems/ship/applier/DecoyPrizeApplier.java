// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INVENTORY family.</b> Bumps {@link Decoy} inventory by one toward
 * {@link DecoyMax}.
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialDecoy} / {@code DecoyMax}
 * inventory caps (REFERENCE.md "Inventory caps and starts" line 372/374).
 * {@code [Misc] DecoyAliveTime} drives the dropped decoy's {@code Decay}
 * lifetime (REFERENCE.md {@code ## Misc} line 164) and is consumed at
 * fire-time by {@code ConsumableSystem}, not here.
 * See {@code ## PrizeWeight} line 242.
 */
public final class DecoyPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(DecoyPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final DecoyMax max = ed.getComponent(ship, DecoyMax.class);
    if (max == null) {
      return;
    }
    final Decoy curr = ed.getComponent(ship, Decoy.class);
    if (curr == null) {
      log.warn(
          "Ship {} has DecoyMax but no Decoy — spawn projection invariant broken; skipping decoy prize",
          ship);
      return;
    }
    if (curr.getCount() < max.getCount()) {
      final int next = curr.getCount() + 1;
      log.info("Ship {} picked up decoy prize and now has {} decoys", ship, next);
      ed.setComponent(ship, new Decoy(next));
    }
  }
}
