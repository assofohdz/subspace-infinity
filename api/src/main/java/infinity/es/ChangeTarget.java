// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Routing component for the Change-entity mutation model decided in
 * <a href="../../../../docs/adr/0001-ecs-component-model.md">ADR 0001</a>.
 *
 * <p>Every transient "Change" holder entity in the new model carries
 * exactly one {@code ChangeTarget} alongside one {@code *Change} /
 * {@code *StatsChange} payload component. The pair tells the canonical
 * writer for that payload type <em>what to mutate</em> ({@link #target})
 * and <em>who is causing the mutation</em> ({@link #source}). The writer
 * filters its {@link com.simsilica.es.EntitySet} on
 * {@code (PayloadChange.class, ChangeTarget.class)} so the framework's
 * component-type narrowing does the dispatch — no enum, no discriminator
 * field, no registration table.
 *
 * <p><b>Both fields are required.</b> {@code source == target} is valid
 * (and common — every self-buff / self-drain emits a Change entity with
 * the ship as both target and source). {@code null} only appears on the
 * no-arg Zay-ES deserialization constructor.
 *
 * <p>{@code source} exists for three reasons called out in the ADR:
 * <ul>
 *   <li>Audit / debugging — "this energy drain came from the cloak
 *       component on ship X" is a real grep when investigating why a
 *       ship's energy is moving.
 *   <li>Self-cancellation patterns — a later Change from the same source
 *       can replace the previous one if the writer chooses to honour
 *       that contract.
 *   <li>Visual / audio attribution — damage numbers point back at the
 *       attacker, kill-attribution chains follow the {@code source}
 *       field of the lethal {@code EnergyChange}.
 * </ul>
 *
 * <p><b>Supersedes {@code Buff(target, startTime)}.</b> The
 * {@link Buff} component carried only a target (and a deferred-start
 * timestamp). The new model splits that into:
 * <ul>
 *   <li>{@code ChangeTarget(target, source)} — required routing on
 *       every Change holder. {@code source} is the new field;
 *       {@code startTime} is dropped (the deferred-buff scheduling
 *       feature is unused — see PRD audit slice).
 *   <li>{@code com.simsilica.es.common.Decay} — temporary vs one-shot
 *       lifetime marker. {@code Decay} presence on the holder entity
 *       distinguishes a buff that reverses on expiry from a one-shot
 *       that the writer destroys after applying. The four-line state
 *       machine in ADR 0001 §"How mutation flows" relies on this rule.
 * </ul>
 *
 * <p><b>Server-only.</b> Like the universal
 * {@link infinity.es.ship.actions.Intent} wrapper this generalises,
 * {@code ChangeTarget} never crosses the wire. Change holder entities
 * are drained the same or next tick by the canonical writer; clients
 * observe the resulting long-lived component value (e.g. {@code Energy},
 * {@code Speed}) via the existing Zay-ES sync. No
 * {@code Serializer.registerClass} call needed.
 *
 * <p><b>Records as EntityComponents.</b> Following the precedent set by
 * {@link infinity.es.ship.actions.Intent}, the no-arg constructor
 * required by {@code .claude/rules/components.md} is provided as an
 * extra constructor that delegates to the canonical record constructor
 * with {@code null} arguments.
 *
 * @param target the entity whose long-lived component is being mutated;
 *               {@code null} only on the no-arg Zay-ES ctor.
 * @param source the entity causing the mutation (often {@code == target}
 *               for self-changes); {@code null} only on the no-arg
 *               Zay-ES ctor.
 * @author Asser Fahrenholz
 */
public record ChangeTarget(EntityId target, EntityId source) implements EntityComponent {

  /**
   * No-arg constructor required by {@code .claude/rules/components.md}
   * for Zay-ES deserialization symmetry. Delegates to the canonical
   * record constructor with {@code null} arguments — a
   * {@code null}-fielded {@code ChangeTarget} would never appear in a
   * real drain (the writer always reads {@link #target} to look up the
   * long-lived component) so the null pair is benign.
   */
  public ChangeTarget() {
    this(null, null);
  }

  /**
   * Construct a {@code ChangeTarget} for a self-change — the common
   * case where a ship's own component mutates a component on itself.
   * Equivalent to {@code new ChangeTarget(self, self)}; named factory
   * so the emit site reads as "this change is targeted at self".
   *
   * @param self the ship / entity whose component is being mutated and
   *             which is also identified as the source of the change;
   *             must not be {@code null}.
   * @return a new {@code ChangeTarget} with {@code target == source == self}.
   */
  public static ChangeTarget self(final EntityId self) {
    return new ChangeTarget(self, self);
  }
}
