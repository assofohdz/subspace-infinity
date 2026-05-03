// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena Repel-effect tuning. Read at fire-time by
 * {@code ConsumableSystem.createRepel} via the attacker's {@code ArenaId}
 * → {@link infinity.settings.ConfigRegistry#weapons()} and projected onto
 * the spawned Repel entity as {@link com.simsilica.es.common.Decay}
 * (deadline = createdTime + {@link #timeMs}),
 * {@link infinity.es.ship.actions.RepelSpeed}, and
 * {@link infinity.es.ship.actions.RepelDistance}.
 *
 * <p>Populated from the merged Groovy fragment store at arena-load — see
 * {@code GroovyWeaponsLoader}. Subspace fragment keys
 * (REFERENCE.md {@code ## Repel}):
 * <ul>
 *   <li>{@code [Repel] RepelSpeed} → {@link #speed} (raw integer, Subspace
 *       velocity units)
 *   <li>{@code [Repel] RepelTime} (centiseconds) × 10 → {@link #timeMs}
 *   <li>{@code [Repel] RepelDistance} → {@link #distancePixels} (Subspace
 *       pixels)
 * </ul>
 *
 * @param speed repulsion speed applied to entities in range
 * @param timeMs effect lifetime in milliseconds (Subspace
 *     {@code RepelTime} converted from centiseconds)
 * @param distancePixels effect radius in Subspace pixels
 */
public record RepelConfig(int speed, long timeMs, int distancePixels) {

  /**
   * Subspace-canonical baseline pulled from the {@code base} preset's
   * {@code [Repel]} section ({@code RepelSpeed 5000},
   * {@code RepelTime 225} cs = {@code 2250} ms, {@code RepelDistance 512}).
   */
  public static final RepelConfig DEFAULTS = new RepelConfig(5000, 2250L, 512);
}
