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
 *   <li>{@code [Repel] RepelDistance} → {@link #distanceTiles}, expressed
 *       in <strong>tiles / world units</strong> (Infinity-native;
 *       {@code 1 unit ≈ 1 tile}). Subspace canon authors this in pixels at
 *       16 px/tile (512 px = 32 tiles); operators porting from SVS divide
 *       by 16. The simulation layer doesn't speak in pixels — see slice 9a
 *       (BombConfig.explodeRadius) for the same precedent.
 * </ul>
 *
 * @param speed repulsion speed applied to entities in range
 * @param timeMs effect lifetime in milliseconds (Subspace
 *     {@code RepelTime} converted from centiseconds)
 * @param distanceTiles effect radius in tiles / world units. Diverges from
 *     Subspace canonical {@code [Repel] RepelDistance} (Subspace pixels at
 *     16 px/tile) — Infinity authors in the native tile / world-unit grid
 *     because pixels are not a meaningful unit at the simulation layer.
 */
public record RepelConfig(int speed, long timeMs, double distanceTiles) {

  /**
   * Subspace-canonical baseline pulled from the {@code base} preset's
   * {@code [Repel]} section ({@code RepelSpeed 5000},
   * {@code RepelTime 225} cs = {@code 2250} ms,
   * {@code RepelDistance 512} px = {@code 32} tiles at the canonical
   * 16 px/tile rate).
   */
  public static final RepelConfig DEFAULTS = new RepelConfig(5000, 2250L, 32.0);
}
