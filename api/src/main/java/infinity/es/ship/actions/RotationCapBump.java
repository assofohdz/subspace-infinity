// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Payload for an upgrade-prize-driven bump to the ship's
 * {@link infinity.es.ship.Rotation} rate. Authored on the emit site
 * wrapped in {@link Intent#of(EntityComponent)}; drained by
 * {@code ShipSpawnSystem}.
 *
 * <p>Emitted by {@code RotationPrizeApplier}. The applier reads
 * {@code RotationUpgrade} off the ship to pick the delta (rad/sec —
 * already converted from Subspace integer rotation units at spawn
 * projection) and emits {@code Intent.of(new RotationCapBump(ship,
 * delta))}. The drain folds deltas per target additively, clamps at
 * {@code RotationMax}, and emits one {@code Rotation} replacement.
 *
 * <p>Delta shape rationale + multi-writer-violation closure: see
 * {@link EnergyCapBump} class Javadoc; same shape, same reasoning.
 *
 * @param target the ship entity whose {@code Rotation} should be bumped
 * @param delta  the additive bump amount in rad/sec (typically the
 *               ship's per-prize {@code RotationUpgrade} value);
 *               pre-clamp
 * @author Asser Fahrenholz
 */
public record RotationCapBump(EntityId target, double delta) implements EntityComponent {

  /** No-arg constructor for Zay-ES — see {@link Intent#Intent()}. */
  public RotationCapBump() {
    this(null, 0.0);
  }
}
