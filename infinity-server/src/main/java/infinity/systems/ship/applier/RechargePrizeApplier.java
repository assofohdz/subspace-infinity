// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.RechargeUpgrade;
import infinity.es.ship.actions.CapBump;
import infinity.es.ship.actions.CapField;
import infinity.es.ship.actions.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Emits an {@link Intent}-wrapped
 * {@link CapBump} payload tagged {@link CapField#RECHARGE} carrying
 * the ship's per-prize {@link RechargeUpgrade} delta (energy/sec —
 * already converted from Subspace raw units at spawn projection); the
 * canonical writer ({@code ShipSpawnSystem}) drains the intent to fold
 * the delta into {@code Recharge} (clamped at {@code RechargeMax}).
 *
 * <p><b>Replacement-as-Mutation</b> — this applier no longer writes
 * {@code Recharge} directly. Same-tick multi-prize pickup accumulates
 * additively per {@link CapBump} class Javadoc. Closes the
 * {@code ShipSpawnSystem} / {@code RechargePrizeApplier} multi-writer
 * violation on the {@code Recharge} component (BACKLOG C2a ship-body).
 *
 * <p>Subspace canonical encoding (REFERENCE.md {@code ## PrizeWeight}
 * lines 235-236, VIE↔UI naming inversion): the class name
 * {@code RechargePrizeApplier} follows the player-facing Continuum UI
 * label "Recharge"; the behaviour (rate boost) matches the VIE
 * {@code .ini}-canonical name {@code QuickCharge} at line 236
 * ({@code QuickCharge (= actual "Recharge")}). The {@code .ini}-
 * labeled {@code Recharge} entry at line 235 is actually the one-shot
 * Full Charge refill — see {@link QuickChargePrizeApplier} for that
 * mirror case. Infinity's class naming follows the player-facing UI
 * labels because that's what arena authors and players think; per-
 * ship Groovy fragments and {@code ## PrizeWeight} fragment authoring
 * still use the VIE {@code .ini}-canonical names.
 *
 * <p>Per-ship caps: {@code [Ship] InitialRecharge} /
 * {@code MaximumRecharge} bound the rate ceiling; per-prize bump amount
 * is {@code [Ship] UpgradeRecharge}.
 */
public final class RechargePrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RechargePrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RechargeUpgrade up = ed.getComponent(ship, RechargeUpgrade.class);
    if (up == null) {
      return;
    }
    final double delta = up.getRechargePerSecondUpgrade();
    if (Double.compare(delta, 0.0) == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} recharge upgrade: emitting cap-bump intent delta={}", ship, delta);
    }
    final EntityId intentId = ed.createEntity();
    ed.setComponent(intentId, Intent.of(ship, new CapBump(CapField.RECHARGE, delta)));
  }
}
