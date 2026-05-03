// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena bomb projectile tuning. Read at projectile-creation time by
 * {@code WeaponsSystem.createProjectileBomb}.
 *
 * <p>Populated from the merged Groovy fragment store at arena-load — see
 * {@code GroovyWeaponsLoader}. Subspace fragment keys:
 * <ul>
 *   <li>{@code [Bomb] BombDamageLevel} → {@link #damage}
 *   <li>{@code [Bomb] BombAliveTime} (centiseconds) × 10 → {@link #decayMs}
 * </ul>
 *
 * @param damage damage applied on detonation
 * @param decayMs bomb projectile lifetime in milliseconds
 */
public record BombConfig(int damage, long decayMs) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Pulled from the {@code base} preset's {@code [Bomb]} section
   * ({@code BombDamageLevel 750}, {@code BombAliveTime 6000}).
   */
  public static final BombConfig DEFAULTS = new BombConfig(750, 60000L);
}
