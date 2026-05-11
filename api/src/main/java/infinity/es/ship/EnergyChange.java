// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Canonical Continuous-half mutation payload for the Energy aspect —
 * additive delta to the live {@link Energy} pool. Pairs with
 * {@link infinity.es.ChangeTarget} on a transient holder entity drained
 * by {@code EnergySystem} per the ADR 0001 Change-entity recipe.
 *
 * <p>Emit shape (server-side, same shape for every emit site):
 *
 * <pre>{@code
 * final EntityId h = ed.createEntity();
 * ed.setComponents(h,
 *     new ChangeTarget(victimId, attackerId),
 *     new EnergyChange(-40));
 * // optional sibling: new DamageSource(attackerId, WeaponType.BOMB)
 * // optional sibling: com.simsilica.es.common.Decay (temporary buff)
 * }</pre>
 *
 * <p><b>Lifetime — distinguished by {@code Decay}-presence</b>
 * (per {@code .claude/rules/decay-ttl.md}):
 * <ul>
 *   <li><b>One-shot</b> (no {@code Decay}): {@code EnergySystem}
 *       applies the delta and destroys the holder entity in the same
 *       tick. Damage hits, weapon-fire cost-deductions, regen ticks,
 *       quick-charge refills.
 *   <li><b>Temporary</b> (with {@code Decay}): {@code EnergySystem}
 *       applies the delta, caches {@code (target, delta)} in a
 *       writer-local map keyed by holder-id, leaves the entity alive.
 *       The central {@code DecaySystem} reaps when the deadline
 *       passes; {@code EnergySystem}'s {@code removedEntities} branch
 *       looks up the cached pair and reverses the delta. Future use:
 *       Cloak / Stealth / Shield mid-game drains and timed buffs.
 * </ul>
 *
 * <p><b>Same-tick multi-source summing.</b> Two emit sites against the
 * same target in the same tick collapse to a single fold-sum
 * {@code setComponent} call on the Energy pool — the writer never
 * applies deltas sequentially. Damage + recharge + cost-deduction in
 * the same tick all stack additively (matches today's
 * {@code Buff + HealthChange} fold behaviour).
 *
 * <p><b>No-op skip.</b> {@code EnergySystem} compares the post-fold
 * value to the current {@link Energy} value and skips the
 * {@code setComponent} call when they match — RaM rule #6 (no spurious
 * {@code changed} events).
 *
 * <p><b>Server-only.</b> Holder entities are drained the same or next
 * tick (Decay case); clients observe the resulting {@link Energy}
 * value via Zay-ES sync. No {@code Serializer.registerClass} needed.
 *
 * <p>Supersedes the pre-ADR {@code Buff(target, startTime) +
 * HealthChange(delta)} pair. {@code source} migrates to
 * {@link infinity.es.ChangeTarget#source()}; the dead-parameter
 * {@code startTime} is dropped (audit-confirmed dead, Task #4).
 *
 * @param delta additive delta to the live {@link Energy} pool;
 *              negative = damage / cost, positive = regen / refill
 * @author Asser Fahrenholz
 */
public record EnergyChange(int delta) implements EntityComponent {

  /**
   * No-arg constructor required by {@code .claude/rules/components.md}
   * for Zay-ES deserialization symmetry.
   */
  public EnergyChange() {
    this(0);
  }
}
