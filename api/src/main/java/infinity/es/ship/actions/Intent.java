// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Universal Replacement-as-Mutation (RaM) intent wrapper. Carries a
 * typed {@code payload} alongside the payload's runtime class so a
 * canonical writer can narrow its {@code EntitySet} to a single
 * payload type via {@code FieldFilter.create(Intent.class, "kind",
 * SomePayload.class)} — the project's canonical narrowing pattern (see
 * {@code PrizeSystem.initialize} for the existing
 * {@code FieldFilter.create(CollisionCategory.class, "filter", …)}
 * precedent).
 *
 * <p>Lives on a short-lived intent holder entity that the matching
 * canonical writer drains and deletes each tick. The wrapper is the
 * <em>only</em> intent-shaped component a future RaM author needs to
 * register — new intent flavours add new payload records, not new
 * top-level component types.
 *
 * <p><b>Pattern (canonical going forward).</b>
 *
 * <p>Author the payload as a plain {@link EntityComponent} record (or
 * class) — one record per logically-distinct intent flavour:
 *
 * <pre>{@code
 * public record EnergyCapBump(EntityId target, int delta)
 *     implements EntityComponent {
 *   public EnergyCapBump() { this(null, 0); }
 * }
 * }</pre>
 *
 * <p>Emit on a short-lived intent entity, deriving {@code kind} from the
 * payload via {@link #of(EntityComponent)} so the emit site can never
 * desync {@code kind} from {@code payload}:
 *
 * <pre>{@code
 * EntityId intentId = ed.createEntity();
 * ed.setComponent(intentId, Intent.of(new EnergyCapBump(ship, delta)));
 * }</pre>
 *
 * <p>Drain in the canonical writer with a {@code FieldFilter}-narrowed
 * EntitySet, then cast the payload back to its concrete record:
 *
 * <pre>{@code
 * this.energyIntents = ed.getEntities(
 *     FieldFilter.create(Intent.class, "kind", EnergyCapBump.class),
 *     Intent.class);
 *
 * // in update():
 * energyIntents.applyChanges();
 * for (final Entity e : energyIntents) {
 *   final EnergyCapBump bump = (EnergyCapBump) e.get(Intent.class).payload();
 *   // fold, clamp, skip-no-op, write …
 * }
 * for (final Entity e : energyIntents) {
 *   ed.removeEntity(e.getId());
 * }
 * }</pre>
 *
 * <p><b>Existing intent shapes predating this wrapper.</b>
 * {@link RocketBuffIntent} (BACKLOG C1) and the
 * {@code Buff + HealthChange} pair drained by {@code EnergySystem}
 * predate the universal wrapper. They are functionally equivalent —
 * fire-and-forget intent components on short-lived holder entities —
 * and remain unmigrated by design (the redesign that introduced
 * {@code Intent} explicitly scoped to C2a cap-bumps to keep the slice
 * mergeable). Both are candidates for a future cleanup pass that
 * unifies them under {@code Intent.of(...)}; their wire-stability
 * concerns (clients filter on {@code HealthChange} via SimEthereal)
 * should drive that decision, not blanket migration.
 *
 * <p><b>Server-only.</b> Like {@link RocketBuffIntent}, the
 * {@code Intent} wrapper never crosses the wire — intents are drained
 * the same or next tick by the canonical writer; clients observe the
 * resulting component replacement (e.g. {@code Energy}) via the
 * existing Zay-ES sync. No {@code Serializer.registerClass} needed.
 *
 * <p><b>Records as EntityComponents.</b> {@code Intent} is the
 * project's first record-shaped {@link EntityComponent} and
 * establishes the convention for the family of payload records that
 * accompany it. The no-arg constructor required by the components rule
 * (see {@code .claude/rules/components.md}) is provided as an extra
 * constructor that delegates to the canonical record constructor with
 * {@code null} arguments — the canonical pattern when records need to
 * satisfy a no-arg-ctor contract.
 *
 * @param kind    the runtime class of {@link #payload} — the narrowing
 *                key for {@code FieldFilter}-driven drain dispatch.
 *                {@code null} only on the no-arg Zay-ES ctor.
 * @param payload the typed intent record. {@code null} only on the
 *                no-arg Zay-ES ctor.
 * @author Asser Fahrenholz
 */
public record Intent(Class<? extends EntityComponent> kind, EntityComponent payload)
    implements EntityComponent {

  /**
   * No-arg constructor required by {@code .claude/rules/components.md}
   * for Zay-ES deserialization symmetry. Delegates to the canonical
   * record constructor with {@code null} arguments — a {@code null}-
   * fielded {@code Intent} would never appear in a real drain (drain
   * sites cast {@link #payload} to a concrete record after the
   * {@code FieldFilter} narrowing already excluded {@code null}-kind
   * intents) so the {@code null} pair is benign.
   */
  public Intent() {
    this(null, null);
  }

  /**
   * Construct an {@code Intent} from a payload, deriving {@code kind}
   * from the payload's runtime class. Use this at the emit site so
   * {@code kind} can never desync from {@code payload} — the only
   * narrowing key a drain ever uses is the runtime class.
   *
   * @param payload the typed intent record; must not be {@code null}
   * @return a new {@code Intent} wrapping {@code payload}
   */
  public static Intent of(final EntityComponent payload) {
    return new Intent(payload.getClass(), payload);
  }
}
