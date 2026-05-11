// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Payload for an upgrade-prize-driven bump to the ship's
 * {@link infinity.es.ship.Recharge} rate. Authored on the emit site
 * wrapped in {@link Intent#of(EntityComponent)}; drained by
 * {@code ShipSpawnSystem}.
 *
 * <p>Emitted by {@code RechargePrizeApplier}. The applier reads
 * {@code RechargeUpgrade} off the ship to pick the delta (energy/sec
 * — already converted from Subspace raw units at spawn projection) and
 * emits {@code Intent.of(new RechargeCapBump(ship, delta))}. The drain
 * folds deltas per target additively, clamps at {@code RechargeMax},
 * and emits one {@code Recharge} replacement.
 *
 * <p>Delta shape rationale + multi-writer-violation closure: see
 * {@link EnergyCapBump} class Javadoc; same shape, same reasoning.
 *
 * @param target the ship entity whose {@code Recharge} should be bumped
 * @param delta  the additive bump amount in energy/sec (typically the
 *               ship's per-prize {@code RechargeUpgrade} value);
 *               pre-clamp
 * @author Asser Fahrenholz
 */
public record RechargeCapBump(EntityId target, double delta) implements EntityComponent {

  /** No-arg constructor for Zay-ES — see {@link Intent#Intent()}. */
  public RechargeCapBump() {
    this(null, 0.0);
  }
}
