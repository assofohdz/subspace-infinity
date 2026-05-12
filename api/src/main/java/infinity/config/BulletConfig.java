// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena bullet projectile tuning; populated from {@code bullet.groovy}. Damage scales as {@code damage + (level-1) * damageUpgrade}. See REFERENCE.md {@code ## Bullet}. */
public record BulletConfig(int damage, int damageUpgrade, long decayMs) {

  /** Subspace-canonical baseline ({@code BulletDamageLevel 100}, {@code BulletDamageUpgrade 50}, {@code BulletAliveTime 5500ms}). */
  public static final BulletConfig DEFAULTS = new BulletConfig(100, 50, 5500L);

  public int damageAtLevel(final int level) {
    return damage + (level - 1) * damageUpgrade;
  }
}
