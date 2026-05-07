// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Marker that opts an entity into the repel-impulse scan in
 * {@code RepelSystem}. When a repel effect entity (carrying
 * {@link infinity.es.ship.actions.RepelSpeed} +
 * {@link infinity.es.ship.actions.RepelDistance}) appears, the system iterates
 * all {@code Repellable} entities, distance-filters by the effect's radius,
 * and stamps an {@code Impulse} pointing away from the explosion center on
 * those in range.
 *
 * <p><b>What carries this marker</b> — driven by per-template Groovy
 * tunables. Today (Slice S5):
 * <ul>
 *   <li>Ships projected with {@link infinity.config.ShipConfig#repellable} = true
 *       (default true).
 *   <li>Bombs (server-side {@code WeaponsSystem.createProjectileBomb}) when
 *       the firing arena's {@link infinity.config.BombConfig#repellable} is
 *       true (default true).
 * </ul>
 *
 * <p>Bullets, bursts, mines, gravbombs are <em>not</em> Repellable today —
 * b2-phased scope per slice S5 grilling. Polish-bag entry covers the
 * follow-up to extend to those projectile families.
 *
 * <p><b>Server-only.</b> Never referenced by client code (the client only
 * sees the resulting body motion via {@code BodyPosition}). No
 * {@code Serializer.registerClass} entry per
 * {@code .claude/rules/components.md}.
 *
 * <p><b>Marker shape</b> — zero-field; presence/absence is the signal. Mass
 * already carried by the body's {@code Mass} component scales the impulse
 * → δ-velocity automatically (heavy ships barely budge, light bombs
 * reverse hard); no explicit "weight" field is needed.
 */
public final class Repellable implements EntityComponent {

  public Repellable() {
    // marker — no fields
  }
}
