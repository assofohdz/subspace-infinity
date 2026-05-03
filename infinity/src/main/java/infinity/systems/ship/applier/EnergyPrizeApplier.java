// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyMax;
import infinity.es.ship.EnergyUpgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Bumps {@link Energy} (the energy <em>cap</em>) by
 * {@link EnergyUpgrade}, clamped at {@link EnergyMax}. Does <em>not</em> touch
 * the live pool — that's {@code QuickChargePrizeApplier}'s job (refills
 * {@code Health} to {@code Energy}).
 */
public final class EnergyPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(EnergyPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final Energy current = ed.getComponent(ship, Energy.class);
    final EnergyMax max = ed.getComponent(ship, EnergyMax.class);
    final EnergyUpgrade up = ed.getComponent(ship, EnergyUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    final int next = Math.min(current.getEnergy() + up.getEnergyUpgrade(), max.getMaxEnergy());
    if (next > current.getEnergy()) {
      log.info("Ship {} energy upgrade: cap {} -> {}", ship, current.getEnergy(), next);
      ed.setComponent(ship, new Energy(next));
    }
  }
}
