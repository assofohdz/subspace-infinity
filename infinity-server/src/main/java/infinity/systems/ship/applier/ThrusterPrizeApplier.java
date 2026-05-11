// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.ThrustUpgrade;
import infinity.es.ship.actions.CapBump;
import infinity.es.ship.actions.CapField;
import infinity.es.ship.actions.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Emits an {@link Intent}-wrapped
 * {@link CapBump} payload tagged {@link CapField#THRUST} carrying the
 * ship's per-prize {@link ThrustUpgrade} delta; the canonical writer
 * ({@code ShipSpawnSystem}) drains the intent to fold the delta into
 * {@code Thrust} (clamped at {@code ThrustMax}). No-op when the upgrade
 * increment is zero (per-arena "no upgrades" design) or when
 * {@code ThrustUpgrade} is missing (spawn projection hasn't run yet).
 *
 * <p><b>Replacement-as-Mutation</b> — this applier no longer writes
 * {@code Thrust} directly. Same-tick multi-prize pickup accumulates
 * additively per {@link CapBump} class Javadoc. Closes the
 * {@code ShipSpawnSystem} (spawn + rocket-buff drain) /
 * {@code ThrusterPrizeApplier} multi-writer violation on the
 * {@code Thrust} component (BACKLOG C2a ship-body).
 *
 * <p><b>Rocket-buff interaction (preserved limitation).</b> The cap-bump
 * drain runs AFTER the rocket-buff drain in {@code ShipSpawnSystem.update},
 * so a thruster prize picked up during an active rocket buff bumps the
 * *buffed* {@code Thrust} value; the buff's revert intent then restores
 * the cached pre-buff snapshot and the prize bump is lost. Same
 * behaviour as before C2a. See {@code RocketSnapshot} class Javadoc.
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialThrust} /
 * {@code MaximumThrust} (REFERENCE.md line 353) plus
 * {@code UpgradeThrust} per-pickup increment; see REFERENCE.md
 * {@code ## PrizeWeight} line 240 ({@code Thruster}) for the prize-name
 * registration.
 */
public final class ThrusterPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(ThrusterPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final ThrustUpgrade up = ed.getComponent(ship, ThrustUpgrade.class);
    if (up == null) {
      return;
    }
    final int delta = up.getThrustUpgrade();
    if (delta == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} thruster upgrade: emitting cap-bump intent delta={}", ship, delta);
    }
    final EntityId intentId = ed.createEntity();
    ed.setComponent(intentId, Intent.of(ship, new CapBump(CapField.THRUST, delta)));
  }
}
