// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Marker opting an entity into {@code RepelSystem}'s impulse scan; stamped by ship + bomb spawn projections
 * per {@code ShipConfig#repellable} / {@code BombConfig#repellable}. Bullets/bursts/mines/gravbombs are not
 * Repellable today (b2-phased scope).
 *
 * <p>Zero-field marker: the body's {@code Mass} scales {@code Impulse} → δ-velocity automatically (heavy
 * ships barely budge, light bombs reverse hard), so no explicit "weight" field is needed.
 */
public final class Repellable implements EntityComponent {

  public Repellable() {
    // marker — no fields
  }
}
