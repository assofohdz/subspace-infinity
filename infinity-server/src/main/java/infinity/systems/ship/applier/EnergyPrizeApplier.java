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
 * {@link ChangeTarget#self(EntityId)} + {@link EnergyStatsChange#ofMax(int)}
 * carrying the ship's per-prize {@link EnergyStats#upgrade()} delta;
 * the canonical writer ({@code EnergyStatsSystem}) drains the Change
 * next tick to fold the delta into {@code EnergyStats.max} (clamped at
 * {@code EnergyStats.hardMax}). Does <em>not</em> touch the live
 * {@code Energy} pool — that's {@code QuickChargePrizeApplier}'s job
 * (refills the pool to the new cap).
 *
 * <p><b>ADR 0001 migration.</b> Was previously
 * {@code Intent.of(ship, new CapBump(CapField.ENERGY, delta))} drained
 * by {@code ShipSpawnSystem}'s {@code drainCapBumpIntents}. Now emits
 * the Change-entity shape per {@code .claude/rules/replacement-as-mutation.md}.
 * Same-tick multi-prize pickup still accumulates additively (each
 * holder emits one delta; the writer folds per ship).
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
    final EnergyStats stats = ed.getComponent(ship, EnergyStats.class);
    if (stats == null) {
      return;
    }
    final int delta = stats.upgrade();
    if (delta == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} energy upgrade: emitting EnergyStatsChange delta={}", ship, delta);
    }
    final EntityId changeId = ed.createEntity();
    ed.setComponents(changeId, ChangeTarget.self(ship), EnergyStatsChange.ofMax(delta));
  }
}
