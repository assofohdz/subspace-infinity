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
      log.info(
          "Ship {} recharge upgrade: energy/sec {} -> {}",
          ship,
          current.getRechargePerSecond(),
          next);
      ed.setComponent(ship, new Recharge(next));
    }
  }
}
