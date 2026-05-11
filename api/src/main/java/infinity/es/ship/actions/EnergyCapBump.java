// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Payload for an upgrade-prize-driven bump to the ship's
 * {@link infinity.es.ship.Energy} cap. Authored on the emit site
 * wrapped in {@link Intent#of(EntityComponent)}; lives on a short-lived
 * intent holder entity that the canonical writer
 * ({@code ShipSpawnSystem.update}) drains and deletes each tick.
 *
 * <p>Emitted by {@code EnergyPrizeApplier} on ENERGY prize pickup. The
 * applier reads {@code EnergyUpgrade} off the ship to pick the delta
 * and emits {@code Intent.of(new EnergyCapBump(ship, delta))} — it
 * never writes {@code Energy} directly. The drain folds deltas per
 * target ship additively (same-tick multi-prize accumulation), clamps
 * at {@code EnergyMax}, and emits one {@code Energy} replacement.
 *
 * <p><b>Design — delta shape (NOT value-replacement).</b> Multiple
 * prize pickups in the same tick (rare but legal — a spawner can
 * release several prizes in the same frame, or two prize spawners can
 * land on the same ship simultaneously) must accumulate. Two same-tick
 * ENERGY pickups should bump the cap by 2× {@code EnergyUpgrade}, not
 * just 1× — that's why the payload carries a delta and the drain
 * sum-folds, instead of writing the absolute post-bump cap value the
 * way {@link RocketBuffIntent} does for the rocket-buff override path.
 *
 * <p>Closes the multi-writer violation between {@code ShipSpawnSystem}
 * (spawn projection) and {@code EnergyPrizeApplier} (upgrade on pickup)
 * documented in {@code .claude/rules/replacement-as-mutation.md}
 * (BACKLOG C2 ship-body group).
 *
 * <p>Server-only — see {@link Intent} class Javadoc for the
 * no-serializer-registration rationale.
 *
 * @param target the ship entity whose {@code Energy} should be bumped
 * @param delta  the additive bump amount (typically the ship's per-
 *               prize {@code EnergyUpgrade} value); pre-clamp
 * @author Asser Fahrenholz
 */
public record EnergyCapBump(EntityId target, int delta) implements EntityComponent {

  /** No-arg constructor for Zay-ES — see {@link Intent#Intent()}. */
  public EnergyCapBump() {
    this(null, 0);
  }
}
