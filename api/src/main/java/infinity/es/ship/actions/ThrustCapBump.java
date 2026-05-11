// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Payload for an upgrade-prize-driven bump to the ship's
 * {@link infinity.es.ship.Thrust}. Authored on the emit site wrapped in
 * {@link Intent#of(EntityComponent)}; drained by
 * {@code ShipSpawnSystem}.
 *
 * <p>Emitted by {@code ThrusterPrizeApplier}. The applier reads
 * {@code ThrustUpgrade} off the ship to pick the delta and emits
 * {@code Intent.of(new ThrustCapBump(ship, delta))}. The drain folds
 * deltas per target additively, clamps at {@code ThrustMax}, and emits
 * one {@code Thrust} replacement.
 *
 * <p>Delta shape rationale + multi-writer-violation closure: see
 * {@link EnergyCapBump} class Javadoc; same shape, same reasoning.
 *
 * <p><b>Rocket-buff interaction (known limitation).</b> The cap-bump
 * drain runs AFTER the {@link RocketBuffIntent} drain in
 * {@code ShipSpawnSystem.update}, so a thruster prize picked up during
 * an active rocket buff bumps the *buffed* {@code Thrust} value. When
 * the buff expires, the revert intent reads the cached pre-buff
 * snapshot from {@link RocketSnapshot} and the prize bump is lost.
 * Pre-existing behaviour from before C2a; preserved deliberately — see
 * {@link RocketSnapshot} Javadoc for the upstream knock-on.
 *
 * @param target the ship entity whose {@code Thrust} should be bumped
 * @param delta  the additive bump amount (typically the ship's
 *               per-prize {@code ThrustUpgrade} value); pre-clamp
 * @author Asser Fahrenholz
 */
public record ThrustCapBump(EntityId target, int delta) implements EntityComponent {

  /** No-arg constructor for Zay-ES — see {@link Intent#Intent()}. */
  public ThrustCapBump() {
    this(null, 0);
  }
}
