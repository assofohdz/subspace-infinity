// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Canonical Stats-half mutation payload for the Energy aspect —
 * partial-record delta against {@link EnergyStats}. Pairs with
 * {@link infinity.es.ChangeTarget} on a transient holder entity drained
 * by {@code EnergyStatsSystem} per the ADR 0001 Change-entity recipe.
 *
 * <p><b>Shape decision — bundled with boxed-null-for-no-change.</b> A
 * single Change record carries one optional delta per
 * {@link EnergyStats} field (boxed types so {@code null} = "leave this
 * field alone"). Rationale:
 * <ul>
 *   <li>Most emit sites bump exactly one field (an ENERGY prize bumps
 *       {@code max}; a RECHARGE prize bumps {@code rechargePerSecond}).
 *       Static factory methods ({@link #ofMax(int)},
 *       {@link #ofRechargePerSecond(double)}, …) keep those call sites
 *       single-line and grep-discoverable.
 *   <li>A future applier wanting to bump multiple fields in one entity
 *       can do so without spawning N holder entities and without the
 *       canonical writer needing to coordinate cross-Change-type
 *       ordering.
 *   <li>Avoids the {@code Per-field Change type} alternative which
 *       would force one canonical writer per Stats field (six
 *       sub-writers, one per record component), an over-engineering
 *       trap for what is fundamentally a fold-and-clamp pass.
 * </ul>
 * The {@code null}-means-no-change discipline is enforced by
 * {@code EnergyStatsSystem.applyDelta} (each field reads its boxed
 * value; {@code null} short-circuits).
 *
 * <p>Emit shape — single-field cap bump:
 *
 * <pre>{@code
 * final EntityId h = ed.createEntity();
 * ed.setComponents(h,
 *     ChangeTarget.self(shipId),
 *     EnergyStatsChange.ofMax(+100));   // ENERGY prize delta
 * }</pre>
 *
 * <p><b>One-shot only.</b> {@link EnergyStats} mutations are
 * prize-driven (acquisition increments) — no canonical "temporary
 * stats buff" mechanic exists in Subspace. The canonical writer
 * destroys the holder entity after applying. If a future feature
 * needs a Decay-bound Stats buff (e.g. "Doublecharge" power-up
 * doubling {@code rechargePerSecond} for 10s), the writer's TrackedApply
 * cache mirrors {@code EnergySystem}'s pattern — see
 * {@code CanonicalWriterDrainTest} for the reference shape.
 *
 * <p><b>Same-tick multi-prize summing.</b> Two emit sites against the
 * same target in the same tick fold additively per field — picking
 * up two ENERGY prizes in one frame stacks the {@code max} delta.
 *
 * <p><b>No-op skip.</b> The canonical writer compares the post-fold,
 * post-clamp value to the current {@link EnergyStats} field and skips
 * the {@code setComponent} when they match — RaM rule #6.
 *
 * <p>Supersedes the pre-ADR {@code Intent + CapBump + CapField.ENERGY}
 * and {@code CapField.RECHARGE} routes for this aspect; those entries
 * are removed from {@code CapField} once the Energy pilot lands and
 * the only consumers (cap-bump appliers) migrate to this Change shape.
 *
 * <p>Server-only — no wire serialization needed (clients observe the
 * resulting {@link EnergyStats} via Zay-ES sync).
 *
 * @param deltaMax              additive delta to {@code EnergyStats.max}
 *                              (current effective cap), or {@code null}
 *                              for no change
 * @param deltaHardMax          additive delta to {@code EnergyStats.hardMax}
 *                              (absolute hard cap), or {@code null}
 * @param deltaUpgrade          additive delta to {@code EnergyStats.upgrade}
 *                              (per-prize-pickup increment for {@code max}),
 *                              or {@code null}
 * @param deltaRechargePerSecond additive delta to
 *                              {@code EnergyStats.rechargePerSecond}, or
 *                              {@code null}
 * @param deltaRechargeMax      additive delta to
 *                              {@code EnergyStats.rechargeMax} (ceiling on
 *                              recharge rate), or {@code null}
 * @param deltaRechargeUpgrade  additive delta to
 *                              {@code EnergyStats.rechargeUpgrade}, or
 *                              {@code null}
 * @author Asser Fahrenholz
 */
public record EnergyStatsChange(
    Integer deltaMax,
    Integer deltaHardMax,
    Integer deltaUpgrade,
    Double deltaRechargePerSecond,
    Double deltaRechargeMax,
    Double deltaRechargeUpgrade)
    implements EntityComponent {

  /**
   * No-arg constructor required by {@code .claude/rules/components.md}
   * for Zay-ES deserialization symmetry. All fields {@code null} —
   * a benign no-op that would never be emitted in practice.
   */
  public EnergyStatsChange() {
    this(null, null, null, null, null, null);
  }

  /**
   * Single-field factory — bump only {@code EnergyStats.max} (current
   * effective cap). Canonical emit shape for {@code EnergyPrizeApplier}.
   *
   * @param delta additive delta (positive = upgrade pickup)
   * @return a Change carrying only the {@code deltaMax} field set
   */
  public static EnergyStatsChange ofMax(final int delta) {
    return new EnergyStatsChange(delta, null, null, null, null, null);
  }

  /**
   * Single-field factory — bump only {@code EnergyStats.hardMax}
   * (absolute hard cap on {@code max}). Reserved for future "raise
   * the ceiling" mechanics; no current emit sites.
   *
   * @param delta additive delta
   * @return a Change carrying only the {@code deltaHardMax} field set
   */
  public static EnergyStatsChange ofHardMax(final int delta) {
    return new EnergyStatsChange(null, delta, null, null, null, null);
  }

  /**
   * Single-field factory — bump only {@code EnergyStats.upgrade}
   * (per-prize-pickup increment for {@code max}). Reserved for future
   * "raise the upgrade size" mechanics; no current emit sites.
   *
   * @param delta additive delta
   * @return a Change carrying only the {@code deltaUpgrade} field set
   */
  public static EnergyStatsChange ofUpgrade(final int delta) {
    return new EnergyStatsChange(null, null, delta, null, null, null);
  }

  /**
   * Single-field factory — bump only
   * {@code EnergyStats.rechargePerSecond}. Canonical emit shape for
   * {@code RechargePrizeApplier}.
   *
   * @param delta additive delta in energy/sec (already converted from
   *              Subspace's per-10-second units at the emit site)
   * @return a Change carrying only the {@code deltaRechargePerSecond}
   *         field set
   */
  public static EnergyStatsChange ofRechargePerSecond(final double delta) {
    return new EnergyStatsChange(null, null, null, delta, null, null);
  }

  /**
   * Single-field factory — bump only {@code EnergyStats.rechargeMax}.
   * Reserved for future "raise the recharge ceiling" mechanics; no
   * current emit sites.
   *
   * @param delta additive delta in energy/sec
   * @return a Change carrying only the {@code deltaRechargeMax} field set
   */
  public static EnergyStatsChange ofRechargeMax(final double delta) {
    return new EnergyStatsChange(null, null, null, null, delta, null);
  }

  /**
   * Single-field factory — bump only
   * {@code EnergyStats.rechargeUpgrade}. Reserved for future "raise
   * the recharge upgrade size" mechanics; no current emit sites.
   *
   * @param delta additive delta in energy/sec
   * @return a Change carrying only the {@code deltaRechargeUpgrade}
   *         field set
   */
  public static EnergyStatsChange ofRechargeUpgrade(final double delta) {
    return new EnergyStatsChange(null, null, null, null, null, delta);
  }
}
