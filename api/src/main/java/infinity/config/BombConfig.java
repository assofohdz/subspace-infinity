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
 *   <li>{@code [Bomb] BombSafety} (0/1) → {@link #bombSafety}, fire-time
 *       gate that rejects bomb fire when an enemy ship sits inside the
 *       firing ship's would-be proximity-arm radius. Self-protect against
 *       blowing yourself up by lobbing a proximity bomb at a hugging
 *       enemy. Auto-no-ops when {@link #proximityDistance} is 0 (nothing
 *       to scan against). See slice 9c-BombSafety.
 *   <li>{@code [Bomb] JitterTime} (centiseconds) × 10 → {@link #jitterTimeMs},
 *       screen-jitter duration on bomb hit. Server stamps a
 *       {@code infinity.es.Jitter} component on each victim that passes the
 *       FF gate; client-side {@code JitterState} reads it on the local avatar
 *       and perturbs the camera with decaying amplitude. Value {@code 0}
 *       disables jitter on this arena. Flat — no per-bomb-level scaling
 *       (REFERENCE.md is silent on per-level; Subspace canon is flat). See
 *       slice 9c-JitterTime.
 * </ul>
 *
 * <p><b>Deviation from canon:</b> REFERENCE.md says the bomb explodes
 * "immediate if ship leaves trigger area" after arming. Infinity's current
 * implementation runs the fuse to completion regardless of whether the
 * ship leaves the radius — operator-noticeable only on near-misses with
 * fast ships. Tracked as a polish-bag follow-up.
 *
 * <p>The remaining {@code [Bomb]} keys (EBombShutdownTime, EBombDamagePercent,
 * BBombDamagePercent) belong to future EMP / bouncing-bomb work.
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
 * @param bombSafety when {@code true}, fire-time scan rejects bomb fire if
 *     any enemy {@link infinity.es.ship.Health}-bearing entity sits inside
 *     the firing ship's effective proximity-arm radius (per-level scaling
 *     via the same formula used at projectile creation). Auto-no-ops when
 *     {@link #proximityDistance} is 0. Subspace canonical key is binary
 *     ({@code BombSafety=0/1}); Infinity stores as a boolean for
 *     consumer-side clarity.
 * @param jitterTimeMs screen-jitter duration in milliseconds applied to each
 *     bomb-damage victim (after the FF gate). Authored as
 *     {@code [Bomb] JitterTime} in centiseconds; the adapter converts ×10 at
 *     the loader boundary. Value 0 disables jitter on this arena. Flat
 *     across bomb levels — REFERENCE.md does not specify per-level scaling.
 * @param repellable when {@code true}, the projectile-spawn projection in
 *     {@code WeaponsSystem.createProjectileBomb} stamps a
 *     {@link infinity.es.Repellable} marker so in-flight bombs get pushed
 *     by a repel within range. Default {@code true} (Subspace canon —
 *     repels reverse incoming bombs, the iconic defensive use case). See
 *     slice S5.
 */
public record BombConfig(
    int damage,
    long decayMs,
    double explodeRadius,
    int proximityDistance,
    long explodeDelayMs,
    boolean bombSafety,
    long jitterTimeMs,
    boolean repellable) {

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
   *
   * <p>{@code bombSafety} defaults to {@code false} so arenas that don't
   * author the key keep slice 9b's "fire is allowed regardless of nearby
   * enemies" behaviour. Active arenas opt in via {@code bombSafety true}.
   *
   * <p>{@code jitterTimeMs} defaults to {@code 0} (disabled): arenas that
   * don't author {@code jitterTimeCs} get no screen jitter. Active arenas
   * opt in via {@code jitterTimeCs} in their {@code bomb.groovy}.
   *
   * <p>{@code repellable} defaults to {@code true} (Subspace canon — repels
   * reverse incoming bombs). Arenas opt out via {@code repellable false}.
   */
  public static final BombConfig DEFAULTS =
      new BombConfig(750, 60000L, 5.0, 0, 0L, false, 0L, true);
}
