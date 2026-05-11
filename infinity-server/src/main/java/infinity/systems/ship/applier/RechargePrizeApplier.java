// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.EnergyStatsChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Emits a Change holder entity carrying
 * {@link ChangeTarget#self(EntityId)} +
 * {@link EnergyStatsChange#ofRechargePerSecond(double)} carrying the
 * ship's per-prize {@link EnergyStats#rechargeUpgrade()} delta
 * (energy/sec — already converted from Subspace per-10-second units
 * at spawn projection); the canonical writer
 * ({@code EnergyStatsSystem}) drains the Change to fold the delta into
 * {@code EnergyStats.rechargePerSecond} (clamped at
 * {@code EnergyStats.rechargeMax}).
 *
 * <p><b>ADR 0001 migration.</b> Was previously
 * {@code Intent.of(ship, new CapBump(CapField.RECHARGE, delta))}
 * drained by {@code ShipSpawnSystem}'s {@code drainCapBumpIntents}.
 * Now emits the Change-entity shape per
 * {@code .claude/rules/replacement-as-mutation.md}. Same-tick
 * multi-prize pickup still accumulates additively.
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
    final EnergyStats stats = ed.getComponent(ship, EnergyStats.class);
    if (stats == null) {
      return;
    }
    final double delta = stats.rechargeUpgrade();
    if (Double.compare(delta, 0.0) == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} recharge upgrade: emitting EnergyStatsChange delta={}", ship, delta);
    }
    final EntityId changeId = ed.createEntity();
    ed.setComponents(
        changeId,
        ChangeTarget.self(ship),
        EnergyStatsChange.ofRechargePerSecond(delta));
  }
}
