// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Unified payload for an upgrade-prize-driven bump to one of the
 * ship's capability caps ({@code Energy} / {@code Recharge} /
 * {@code Rotation} / {@code Thrust} / {@code Speed}), selected by the
 * {@link #field()} discriminator. Authored on the emit site wrapped in
 * {@link Intent#of(com.simsilica.es.EntityId, EntityComponent)}; lives
 * on a short-lived intent holder entity that the canonical writer
 * ({@code ShipSpawnSystem.update}) drains and deletes each tick.
 *
 * <p>The {@code target} ship lives on the {@link Intent} wrapper —
 * <em>not</em> on this payload — so every cap-bump emit site uses the
 * same {@code Intent.of(target, new CapBump(field, delta))} shape and
 * the drain can read the target uniformly off the wrapper.
 *
 * <p>Emitted by the five capability prize appliers
 * ({@code EnergyPrizeApplier}, {@code RechargePrizeApplier},
 * {@code RotationPrizeApplier}, {@code ThrusterPrizeApplier},
 * {@code TopSpeedPrizeApplier}). Each reads its matching
 * {@code *Upgrade} component off the ship to pick the delta and emits
 * one {@code Intent}-wrapped {@code CapBump} — never writes the cap
 * component directly. The drain folds deltas per
 * {@code (target, field)} additively (same-tick multi-prize
 * accumulation), clamps at the matching {@code *Max}, and emits one
 * component replacement per tuple.
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
 * <p><b>Delta type — {@code double} for the unified shape.</b> The
 * int-backed capabilities ({@code Energy} / {@code Thrust} /
 * {@code Speed}) round half-away-from-zero via {@link Math#round} at
 * apply time; the double-backed capabilities ({@code Recharge} /
 * {@code Rotation}) pass through directly. See {@link CapField} for
 * the per-field dispatch and the rounding policy. Current callers
 * (the five appliers) integerize at the source — they read
 * int-typed {@code *Upgrade} components — so the widening to double
 * loses no precision today; the rounding policy is only relevant if
 * a future caller emits a fractional delta against an int-backed
 * field.
 *
 * <p>Closes the multi-writer violation between {@code ShipSpawnSystem}
 * (spawn projection) and each of the five prize appliers documented
 * in {@code .claude/rules/replacement-as-mutation.md} (BACKLOG C2
 * ship-body group).
 *
 * <p>Server-only — see {@link Intent} class Javadoc for the
 * no-serializer-registration rationale.
 *
 * @param field the capability cap to bump (selects the read/write/
 *              clamp dispatch in {@link CapField#apply})
 * @param delta the additive bump amount (typically the ship's per-
 *              prize {@code *Upgrade} value); pre-clamp; widened to
 *              {@code double} so every cap-bump has the same emit
 *              shape regardless of the field's backing type
 * @author Asser Fahrenholz
 */
public record CapBump(CapField field, double delta) implements EntityComponent {

  /** No-arg constructor for Zay-ES — see {@link Intent#Intent()}. */
  public CapBump() {
    this(null, 0.0);
  }
}
