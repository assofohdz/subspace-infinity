// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;
import infinity.es.ship.ThrustUpgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Bumps {@link Thrust} by {@link ThrustUpgrade},
 * clamped at {@link ThrustMax}. No-op when at the cap, when the upgrade
 * increment is zero (per-arena "no upgrades" design), or when any of the
 * three components is missing (spawn projection hasn't run yet).
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialThrust} /
 * {@code MaximumThrust} (REFERENCE.md line 353) plus {@code UpgradeThrust}
 * per-pickup increment; see REFERENCE.md {@code ## PrizeWeight} line 240
 * ({@code Thruster}) for the prize-name registration.
 */
public final class ThrusterPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(ThrusterPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final Thrust current = ed.getComponent(ship, Thrust.class);
    final ThrustMax max = ed.getComponent(ship, ThrustMax.class);
    final ThrustUpgrade up = ed.getComponent(ship, ThrustUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    final int next = Math.min(current.getThrust() + up.getThrustUpgrade(), max.getThrustMax());
    if (next > current.getThrust()) {
      if (log.isInfoEnabled()) {
        log.info("Ship {} thruster upgrade: thrust {} -> {}", ship, current.getThrust(), next);
      }
      ed.setComponent(ship, new Thrust(next));
    }
  }
}
