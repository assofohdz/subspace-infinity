// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena bullet projectile tuning. Read at projectile-creation time by
 * {@code WeaponsSystem.createProjectileBullet} via the attacker's
 * {@code ArenaId} → {@link infinity.settings.ConfigRegistry#weapons()}.
 *
 * <p>Populated from the merged Groovy fragment store at arena-load — see
 * {@code GroovyWeaponsLoader}. Subspace fragment keys:
 * <ul>
 *   <li>{@code [Bullet] BulletDamageLevel} → {@link #damage} (level-1 base)
 *   <li>{@code [Bullet] BulletDamageUpgrade} → {@link #damageUpgrade}
 *       (per-level increment)
 *   <li>{@code [Bullet] BulletAliveTime} (centiseconds) × 10 → {@link #decayMs}
 * </ul>
 *
 * <p>Subspace bullet-damage scaling formula: {@code damageAtLevel(N) =
 * damage + (N - 1) * damageUpgrade}. Set {@code damageUpgrade = 0} for
 * presets that want all bullet levels to deal identical damage (e.g.
 * {@code svs-pb} where only the visual sprite changes per level).
 *
 * @param damage damage applied at bullet level 1
 * @param damageUpgrade additional damage per bullet level above 1
 * @param decayMs lifetime in milliseconds before the projectile expires
 */
public record BulletConfig(int damage, int damageUpgrade, long decayMs) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Pulled from the {@code svs} preset's {@code [Bullet]} section
   * ({@code BulletDamageLevel 100}, {@code BulletDamageUpgrade 50},
   * {@code BulletAliveTime 550}).
   */
  public static final BulletConfig DEFAULTS = new BulletConfig(100, 50, 5500L);

  /**
   * Bullet damage at bullet level {@code level} (1-based, matching
   * {@code BulletLevel.LEVEL_1}..{@code LEVEL_4}). Subspace formula:
   * {@code damage + (level - 1) * damageUpgrade}.
   */
  public int damageAtLevel(final int level) {
    return damage + (level - 1) * damageUpgrade;
  }
}
