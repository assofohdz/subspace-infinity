// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.EnergyUpgrade;
import infinity.es.ship.actions.EnergyCapBump;
import infinity.es.ship.actions.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Emits an {@link Intent}-wrapped
 * {@link EnergyCapBump} payload carrying the ship's per-prize
 * {@link EnergyUpgrade} delta; the canonical writer
 * ({@code ShipSpawnSystem}) drains the intent next tick to fold the
 * delta into {@code Energy} (clamped at {@code EnergyMax}). Does
 * <em>not</em> touch the live pool — that's
 * {@code QuickChargePrizeApplier}'s job (refills {@code Health} to
 * {@code Energy}).
 *
 * <p><b>Replacement-as-Mutation</b> — this applier no longer writes
 * {@code Energy} directly. Same-tick multi-prize pickup accumulates
 * additively per {@link EnergyCapBump} class Javadoc. Closes the
 * {@code ShipSpawnSystem} / {@code EnergyPrizeApplier} multi-writer
 * violation on the {@code Energy} component (BACKLOG C2a ship-body).
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialEnergy} /
 * {@code MaximumEnergy} bound the cap; per-prize bump amount is
 * {@code [Ship] UpgradeEnergy}. Prize-weight entry: REFERENCE.md
 * {@code ## PrizeWeight} line 237 ({@code Energy (= "Energy Upgrade")}).
 */
public final class EnergyPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(EnergyPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final EnergyUpgrade up = ed.getComponent(ship, EnergyUpgrade.class);
    if (up == null) {
      return;
    }
    final int delta = up.getEnergyUpgrade();
    if (delta == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} energy upgrade: emitting cap-bump intent delta={}", ship, delta);
    }
    final EntityId intentId = ed.createEntity();
    ed.setComponent(intentId, Intent.of(new EnergyCapBump(ship, delta)));
  }
}
