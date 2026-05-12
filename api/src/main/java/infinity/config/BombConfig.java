// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena bomb projectile tuning; populated from {@code bomb.groovy} via {@code BombAdapter}. {@code explodeRadius} authored in tiles (canon {@code BombExplodePixels} ÷ 16); proximity disabled when {@code proximityDistance}/{@code explodeDelayMs} is 0. See REFERENCE.md {@code ## Bomb}. */
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
