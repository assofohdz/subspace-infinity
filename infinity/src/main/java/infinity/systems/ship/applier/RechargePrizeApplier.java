// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.Recharge;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.RechargeUpgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Bumps {@link Recharge} by {@link RechargeUpgrade},
 * clamped at {@link RechargeMax}. Values are in energy/sec — the raw Subspace
 * recharge units are converted by {@code ShipSpawnSystem} at spawn.
 *
 * <p>Subspace canonical encoding (REFERENCE.md {@code ## PrizeWeight} lines
 * 235-236, VIE↔UI naming inversion): the class name {@code RechargePrizeApplier}
 * follows the player-facing Continuum UI label "Recharge"; the behaviour
 * (rate boost) matches the VIE {@code .ini}-canonical name {@code QuickCharge}
 * at line 236 ({@code QuickCharge (= actual "Recharge")}). The
 * {@code .ini}-labeled {@code Recharge} entry at line 235 is actually the
 * one-shot Full Charge refill — see {@link QuickChargePrizeApplier} for that
 * mirror case. Infinity's class naming follows the player-facing UI labels
 * because that's what arena authors and players think; per-ship Groovy
 * fragments and {@code ## PrizeWeight} fragment authoring still use the
 * VIE {@code .ini}-canonical names.
 *
 * <p>Per-ship caps: {@code [Ship] InitialRecharge} / {@code MaximumRecharge}
 * bound the rate ceiling; per-prize bump amount is {@code [Ship] UpgradeRecharge}.
 */
public final class RechargePrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RechargePrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final Recharge current = ed.getComponent(ship, Recharge.class);
    final RechargeMax max = ed.getComponent(ship, RechargeMax.class);
    final RechargeUpgrade up = ed.getComponent(ship, RechargeUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    final double next =
        Math.min(
            current.getRechargePerSecond() + up.getRechargePerSecondUpgrade(),
            max.getMaxRechargePerSecond());
    if (next > current.getRechargePerSecond()) {
      if (log.isInfoEnabled()) {
        log.info(
            "Ship {} recharge upgrade: energy/sec {} -> {}",
            ship,
            current.getRechargePerSecond(),
            next);
      }
      ed.setComponent(ship, new Recharge(next));
    }
  }
}
