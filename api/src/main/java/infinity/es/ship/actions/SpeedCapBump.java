// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Payload for an upgrade-prize-driven bump to the ship's
 * {@link infinity.es.ship.Speed} cap. Authored on the emit site wrapped
 * in {@link Intent#of(EntityComponent)}; drained by
 * {@code ShipSpawnSystem}.
 *
 * <p>Emitted by {@code TopSpeedPrizeApplier}. The applier reads
 * {@code SpeedUpgrade} off the ship to pick the delta and emits
 * {@code Intent.of(new SpeedCapBump(ship, delta))}. The drain folds
 * deltas per target additively, clamps at {@code SpeedMax}, and emits
 * one {@code Speed} replacement.
 *
 * <p>Delta shape rationale + multi-writer-violation closure: see
 * {@link EnergyCapBump} class Javadoc; same shape, same reasoning.
 *
 * <p><b>Rocket-buff interaction (known limitation).</b> Same shape as
 * {@link ThrustCapBump} — a topspeed prize picked up during an active
 * rocket buff is lost on revert. See {@link ThrustCapBump} and
 * {@link RocketSnapshot} for the upstream knock-on.
 *
 * @param target the ship entity whose {@code Speed} should be bumped
 * @param delta  the additive bump amount (typically the ship's
 *               per-prize {@code SpeedUpgrade} value); pre-clamp
 * @author Asser Fahrenholz
 */
public record SpeedCapBump(EntityId target, int delta) implements EntityComponent {

  /** No-arg constructor for Zay-ES — see {@link Intent#Intent()}. */
  public SpeedCapBump() {
    this(null, 0);
  }
}
