// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena burst-firing tuning. Read at burst-firing time by
 * {@code WeaponsSystem.createProjectileBurst} — note this is distinct from
 * {@link CountStats} (per-ship burst inventory cap) which lives in
 * {@link ShipConfig#bursts()}.
 *
 * <p>Populated from the merged Groovy fragment store at arena-load — see
 * {@code GroovyWeaponsLoader}. Subspace fragment keys:
 * <ul>
 *   <li>{@code [Burst] BurstDamageLevel} → {@link #damage}
 * </ul>
 * (Subspace VIE has no canonical {@code BurstShrapnelCount} or
 * {@code BurstAliveTime} keys; {@link #projectileCount} and {@link #decayMs}
 * stay on their Infinity-default values.)
 *
 * @param projectileCount number of bullets emitted in a single burst
 *     (Infinity default {@code 30}, evenly distributed around the firing ship)
 * @param decayMs lifetime in ms of each individual burst-projectile bullet
 *     (Infinity default {@code 1500} — same as {@link BulletConfig#decayMs}
 *     for the historical routing)
 * @param damage damage applied on burst-bullet hit
 */
public record BurstFireConfig(long projectileCount, long decayMs, int damage) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Damage pulled from the {@code svs} preset's {@code [Burst]} section
   * ({@code BurstDamageLevel 250}); count / decay keep Infinity defaults.
   */
  public static final BurstFireConfig DEFAULTS = new BurstFireConfig(30L, 1500L, 250);
}
