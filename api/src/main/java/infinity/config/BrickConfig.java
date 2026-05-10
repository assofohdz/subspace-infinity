// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena brick tuning. Read at fire-time by
 * {@code ConsumableSystem} via the attacker's {@code ArenaId} →
 * {@link infinity.settings.ConfigRegistry#brick()} and projected onto
 * the spawned brick marker entity as
 * {@link com.simsilica.es.common.Decay} (deadline = createdTime +
 * {@link #timeMs}) and
 * {@link infinity.es.ship.actions.BrickSpan}.
 *
 * <p>Subspace fragment keys (REFERENCE.md {@code ## Brick}):
 * <ul>
 *   <li>{@code [Brick] BrickTime} (centiseconds) × 10 → {@link #timeMs}
 *   <li>{@code [Brick] BrickSpan} (tile count) → {@link #spanTiles}
 * </ul>
 *
 * <p>Slice 3 ships plumbing only — the marker entity carries the span +
 * decay deadline but no shape, no contact handler, no client visual.
 * Making bricks solid obstacles (the canonical Subspace mechanic) is a
 * separate follow-up slice that adds {@code ShapeNames.BRICK}, brick-
 * vs-ship/bullet/bomb collision filters, and the per-tile geometry
 * math.
 *
 * @param spanTiles wall length in tiles (Subspace canonical: 7 in base,
 *     up to 20 in svs)
 * @param timeMs brick lifetime in milliseconds (Subspace
 *     {@code BrickTime} converted from centiseconds)
 */
public record BrickConfig(int spanTiles, long timeMs) {

  /**
   * Subspace-canonical baseline pulled from the {@code base} preset's
   * {@code [Brick]} section ({@code BrickSpan 7},
   * {@code BrickTime 12000} cs = {@code 120000} ms).
   */
  public static final BrickConfig DEFAULTS = new BrickConfig(7, 120_000L);
}
