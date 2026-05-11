// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.EnergyStatsChange;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical writer for the {@link EnergyStats} component (the bundled
 * Stats record carrying current/max/upgrade for both the energy cap
 * and the recharge rate). Drains {@link EnergyStatsChange} +
 * {@link ChangeTarget} holder entities each tick; folds per-field
 * deltas additively per target; clamps {@code max} at {@code hardMax}
 * and {@code rechargePerSecond} at {@code rechargeMax}; skip-no-op on
 * unchanged records.
 *
 * <p><b>ADR 0001 canonical-writer recipe</b>
 * (see {@code .claude/rules/replacement-as-mutation.md}):
 *
 * <ul>
 *   <li>One {@link EntitySet} keyed on
 *       {@code (EnergyStatsChange.class, ChangeTarget.class)} — Zay-ES
 *       component-type narrowing dispatches; no enum / registration
 *       table.
 *   <li>Per-tick per-field fold by target — multi-prize summing.
 *   <li>{@link Decay}-presence check at apply time distinguishes
 *       one-shot (destroy immediately) from temporary (track for
 *       reversal). Today's appliers all emit one-shot; the
 *       temporary path is wired for future "Doublecharge-style"
 *       timed stats buffs.
 *   <li>Skip-no-op writes — if every post-fold field equals the
 *       current record's field, no {@code setComponent} call fires
 *       (RaM rule #6).
 * </ul>
 *
 * <p><b>Critical: cache (target, delta) at apply-time for Decay-bound
 * Changes</b> (Phase 0 Task #3 finding). On Decay-driven removal the
 * writer must NOT read {@link ChangeTarget} or {@link EnergyStatsChange}
 * off the removed-entity snapshot. We cache the
 * {@code (target, EnergyStatsChange)} tuple at apply-time keyed by the
 * holder's {@link EntityId} and reverse from the cache on remove. The
 * cached payload is the original Change record; reversal flips the
 * sign of every present (non-null) delta field.
 *
 * <p><b>System registration order:</b> registered <em>before</em> the
 * central {@code DecaySystem} in {@code GameServer} for the same
 * reason as {@code EnergySystem} — the writer's drain must run before
 * the reaper destroys a Decay-bound holder so the apply-time
 * (target, delta) capture happens.
 *
 * <p><b>Companion writer:</b> {@code EnergySystem} drains the live
 * pool ({@link infinity.es.ship.Energy}). The two systems are
 * decoupled by design — a stats bump (e.g. ENERGY prize raising the
 * cap) does NOT auto-refill the pool; the QUICKCHARGE prize is the
 * one-shot refill mechanic. If a future feature wants "raising the
 * cap mid-fight also raises the current pool by the same amount", the
 * applier emits both an {@code EnergyStatsChange} and an
 * {@code EnergyChange} on separate holder entities.
 *
 * @author Asser Fahrenholz
 */
public class EnergyStatsSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet stats;
  private EntitySet changes;

  /**
   * Per-Change-entity record of "what we applied where" — captured at
   * apply-time so we can reverse on Decay-reaper removal without
   * depending on the removed-entity component snapshot. Keyed by
   * Change holder {@link EntityId}; value holds the target plus the
   * full {@link EnergyStatsChange} record so reversal can flip the
   * sign of every present field.
   */
  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  /**
   * Pair captured at apply-time for a temporary Change entity: the
   * {@link ChangeTarget#target()} the writer mutated and the full
   * {@link EnergyStatsChange} payload it folded into the target's
   * {@link EnergyStats} (which must be reversed on Decay-reaper
   * removal).
   */
  private record TrackedApply(EntityId target, EnergyStatsChange payload) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    stats = ed.getEntities(EnergyStats.class);
    changes = ed.getEntities(EnergyStatsChange.class, ChangeTarget.class);
  }

  @Override
  protected void terminate() {
    stats.release();
    stats = null;
    changes.release();
    changes = null;
  }

  @Override
  public void update(final SimTime time) {
    stats.applyChanges();
    changes.applyChanges();

    // Phase 1 — fold added per-target. Bundled-record fold keeps the
    // payloads themselves; same-tick multi-prize merges into one
    // composite payload by summing each field's non-null delta.
    final Map<EntityId, EnergyStatsChange> folded = new HashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final EnergyStatsChange payload = added.get(EnergyStatsChange.class);
      folded.merge(ct.target(), payload, EnergyStatsSystem::mergeChanges);
      final Decay decay = ed.getComponent(added.getId(), Decay.class);
      if (decay == null) {
        oneShotHolders.add(added.getId());
      } else {
        // Cache the payload AS APPLIED so reversal flips the sign of
        // each present field.
        trackedApplied.put(added.getId(), new TrackedApply(ct.target(), payload));
      }
    }

    // Phase 2 — apply per target (read current stats, fold each field,
    // clamp, skip-no-op).
    for (final Map.Entry<EntityId, EnergyStatsChange> e : folded.entrySet()) {
      applyDelta(e.getKey(), e.getValue());
    }

    // Phase 3 — destroy one-shot holders.
    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }

    // Phase 4 — reverse on Decay-reaper removal.
    for (final Entity removed : changes.getRemovedEntities()) {
      final TrackedApply applied = trackedApplied.remove(removed.getId());
      if (applied == null) {
        continue;
      }
      applyDelta(applied.target(), negate(applied.payload()));
    }
  }

  /**
   * Apply a (possibly-folded) {@link EnergyStatsChange} payload to
   * {@code target}'s {@link EnergyStats}: per-field add, clamp
   * {@code max} at {@code hardMax} and {@code rechargePerSecond} at
   * {@code rechargeMax}, skip the write if the post-fold record equals
   * the current one.
   */
  private void applyDelta(final EntityId target, final EnergyStatsChange payload) {
    final EnergyStats current = ed.getComponent(target, EnergyStats.class);
    if (current == null) {
      // Defensive — Change emitted before spawn projection landed.
      return;
    }
    final int nextHardMax = applyInt(current.hardMax(), payload.deltaHardMax());
    final int nextUpgrade = applyInt(current.upgrade(), payload.deltaUpgrade());
    int nextMax = applyInt(current.max(), payload.deltaMax());
    // Clamp max at hardMax.
    if (nextMax > nextHardMax) {
      nextMax = nextHardMax;
    }
    final double nextRechargeMax =
        applyDouble(current.rechargeMax(), payload.deltaRechargeMax());
    final double nextRechargeUpgrade =
        applyDouble(current.rechargeUpgrade(), payload.deltaRechargeUpgrade());
    double nextRechargePerSecond =
        applyDouble(current.rechargePerSecond(), payload.deltaRechargePerSecond());
    if (nextRechargePerSecond > nextRechargeMax) {
      nextRechargePerSecond = nextRechargeMax;
    }
    final EnergyStats next =
        new EnergyStats(
            nextMax,
            nextHardMax,
            nextUpgrade,
            nextRechargePerSecond,
            nextRechargeMax,
            nextRechargeUpgrade);
    if (next.equals(current)) {
      // RaM rule #6 — skip no-op writes.
      return;
    }
    ed.setComponent(target, next);
  }

  /** Fold two boxed-null Change records into one per-field merge. */
  private static EnergyStatsChange mergeChanges(
      final EnergyStatsChange a, final EnergyStatsChange b) {
    return new EnergyStatsChange(
        sumInt(a.deltaMax(), b.deltaMax()),
        sumInt(a.deltaHardMax(), b.deltaHardMax()),
        sumInt(a.deltaUpgrade(), b.deltaUpgrade()),
        sumDouble(a.deltaRechargePerSecond(), b.deltaRechargePerSecond()),
        sumDouble(a.deltaRechargeMax(), b.deltaRechargeMax()),
        sumDouble(a.deltaRechargeUpgrade(), b.deltaRechargeUpgrade()));
  }

  /** Negate every present (non-null) field on a Change record. */
  private static EnergyStatsChange negate(final EnergyStatsChange p) {
    return new EnergyStatsChange(
        p.deltaMax() == null ? null : -p.deltaMax(),
        p.deltaHardMax() == null ? null : -p.deltaHardMax(),
        p.deltaUpgrade() == null ? null : -p.deltaUpgrade(),
        p.deltaRechargePerSecond() == null ? null : -p.deltaRechargePerSecond(),
        p.deltaRechargeMax() == null ? null : -p.deltaRechargeMax(),
        p.deltaRechargeUpgrade() == null ? null : -p.deltaRechargeUpgrade());
  }

  private static Integer sumInt(final Integer a, final Integer b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  private static Double sumDouble(final Double a, final Double b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  private static int applyInt(final int current, final Integer delta) {
    return delta == null ? current : current + delta;
  }

  private static double applyDouble(final double current, final Double delta) {
    return delta == null ? current : current + delta;
  }
}
