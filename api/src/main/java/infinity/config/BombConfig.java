// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena bomb projectile tuning; populated from {@code bomb.groovy} via {@code BombAdapter}. See REFERENCE.md {@code ## Bomb}.
 *
 * <p>Per-level scaling at fire time: {@code explodeRadius} multiplicative (L1×1..L4×4); {@code proximityDistance} additive (L1+0..L4+3).
 * {@code explodeRadius} is authored in tiles (canon {@code BombExplodePixels} ÷ 16); centisecond timing keys (×10 → ms) live on the loader.
 * Proximity fuse is disabled when either {@code proximityDistance} or {@code explodeDelayMs} is 0 (falls back to direct-contact).
 *
 * <p><b>Divergence:</b> REFERENCE.md says the bomb detonates "immediate if ship leaves trigger area" after arming.
 * Infinity runs the fuse to completion regardless. Polish-bag follow-up.
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

  /** Subspace-canonical baseline; proximity / safety / jitter default disabled, {@code repellable true} per canon. */
  public static final BombConfig DEFAULTS =
      new BombConfig(750, 60000L, 5.0, 0, 0L, false, 0L, true);
}
