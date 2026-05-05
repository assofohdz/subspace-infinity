// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena bomb projectile tuning. Read at projectile-creation time by
 * {@code WeaponsSystem.createProjectileBomb}.
 *
 * <p>Populated from the typed {@code bomb.groovy} fragment by
 * {@code BombAdapter}. Subspace fragment keys:
 * <ul>
 *   <li>{@code [Bomb] BombDamageLevel} → {@link #damage}
 *   <li>{@code [Bomb] BombAliveTime} (centiseconds) × 10 → {@link #decayMs}
 *   <li>{@code [Bomb] BombExplodePixels} → {@link #explodeRadius}, expressed
 *       in <strong>tiles / world units</strong> (Infinity-native;
 *       {@code 1 unit ≈ 1 tile}). Subspace canon authors this in pixels at
 *       16 px/tile (80 px = 5 tiles); operators porting from SVS divide by
 *       16. The fire-time projection multiplies by the firing ship's bomb
 *       level (L1×1, L2×2, L3×3, L4×4) per REFERENCE.md ## Bomb. See slice
 *       9a.
 * </ul>
 *
 * <p>The remaining {@code [Bomb]} keys (BombExplodeDelay, ProximityDistance,
 * JitterTime, BombSafety) are scoped to slices 9b/9c — they require the
 * proximity-arming + fuse infrastructure (9b) and the safety/jitter polish
 * (9c) that 9a intentionally defers.
 *
 * @param damage damage applied on detonation
 * @param decayMs bomb projectile lifetime in milliseconds
 * @param explodeRadius base (L1) blast radius in tiles / world units;
 *     per-level multiplier (×level: L1=1×, L2=2×, L3=3×, L4=4×) is applied
 *     at fire time when projecting onto the bomb's {@code SplashDamage}
 *     component. Diverges from Subspace canonical
 *     {@code [Bomb] BombExplodePixels} (Subspace pixels at 16 px/tile) —
 *     Infinity authors in the native tile / world-unit grid because pixels
 *     are not a meaningful unit at the simulation layer.
 */
public record BombConfig(int damage, long decayMs, double explodeRadius) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Pulled from the {@code base} preset's {@code [Bomb]} section
   * ({@code BombDamageLevel 750}, {@code BombAliveTime 6000}) plus the
   * Subspace SVS-canon {@code BombExplodePixels 80} expressed in Infinity
   * units (= 5 tiles at the canonical 16 px/tile rate).
   */
  public static final BombConfig DEFAULTS = new BombConfig(750, 60000L, 5.0);
}
