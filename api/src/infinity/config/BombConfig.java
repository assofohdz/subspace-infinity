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
 *   <li>{@code [Bomb] ProximityDistance} → {@link #proximityDistance}, base
 *       (L1) proximity-trigger radius in <strong>tiles</strong>. Per-level
 *       additive scaling (L1=base, L2=base+1, L3=base+2, L4=base+3) per
 *       REFERENCE.md ## Bomb — distinct from the multiplicative scaling on
 *       {@link #explodeRadius}. Value {@code 0} disables the proximity
 *       fuse on this arena and bombs fall back to direct-contact
 *       detonation. See slice 9b.
 *   <li>{@code [Bomb] BombExplodeDelay} (centiseconds) × 10 →
 *       {@link #explodeDelayMs}, fuse delay between proximity arming and
 *       detonation. Value {@code 0} disables the proximity fuse. See
 *       slice 9b.
 * </ul>
 *
 * <p><b>Deviation from canon:</b> REFERENCE.md says the bomb explodes
 * "immediate if ship leaves trigger area" after arming. Infinity's current
 * implementation runs the fuse to completion regardless of whether the
 * ship leaves the radius — operator-noticeable only on near-misses with
 * fast ships. Tracked as a polish-bag follow-up.
 *
 * <p>The remaining {@code [Bomb]} keys (JitterTime, BombSafety,
 * EBombShutdownTime, EBombDamagePercent, BBombDamagePercent) belong to
 * slices 9c (safety + jitter) or future EMP / bouncing-bomb work.
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
 * @param proximityDistance base (L1) proximity-arm radius in tiles. Per-level
 *     additive scaling (+1 per level above L1) is applied at fire time when
 *     projecting onto the bomb's {@code ProximityFuse} component. Value 0
 *     disables proximity arming on this arena (bombs use direct-contact
 *     detonation only).
 * @param explodeDelayMs fuse delay in milliseconds between proximity arming
 *     and detonation. Value 0 disables proximity arming on this arena.
 *     Both this and {@link #proximityDistance} must be &gt; 0 for the
 *     proximity-fuse path to engage; either being 0 falls back to
 *     direct-contact detonation.
 */
public record BombConfig(
    int damage,
    long decayMs,
    double explodeRadius,
    int proximityDistance,
    long explodeDelayMs) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Pulled from the {@code base} preset's {@code [Bomb]} section
   * ({@code BombDamageLevel 750}, {@code BombAliveTime 6000}) plus the
   * Subspace SVS-canon {@code BombExplodePixels 80} expressed in Infinity
   * units (= 5 tiles at the canonical 16 px/tile rate).
   *
   * <p>Proximity defaults are {@code 0/0} (disabled): an arena that
   * doesn't author the new keys keeps Slice 9a's direct-contact +
   * splash-on-impact behaviour. Active arenas opt in by setting both
   * {@code proximityDistance} and {@code explodeDelayCs} in their
   * {@code bomb.groovy}.
   */
  public static final BombConfig DEFAULTS = new BombConfig(750, 60000L, 5.0, 0, 0L);
}
