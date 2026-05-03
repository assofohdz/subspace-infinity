// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena bundle of weapon-projectile tuning. Held on the per-arena
 * {@link infinity.settings.ConfigRegistry} snapshot and read by
 * {@code WeaponsSystem} / {@code ConsumableSystem} at projectile-creation time
 * via the attacker's {@code ArenaId}.
 *
 * <p>Today every arena receives {@link #DEFAULTS} (matching the legacy Java
 * constants); per-arena overrides are the Phase B work of bridging the
 * untyped Groovy fragment values (e.g. {@code [Bullet] BulletDamageLevel
 * 100}) into these typed records.
 *
 * @param bullet gun-bullet projectile tuning
 * @param bomb bomb projectile tuning
 * @param gravBomb gravity-bomb / wormhole tuning
 * @param mine mine projectile tuning
 * @param thor Thor projectile tuning
 * @param burst burst-firing tuning (projectile count + per-projectile decay)
 * @param repel Repel-effect tuning (speed / time / distance)
 */
public record WeaponsConfig(
    BulletConfig bullet,
    BombConfig bomb,
    GravBombConfig gravBomb,
    MineConfig mine,
    ThorConfig thor,
    BurstFireConfig burst,
    RepelConfig repel) {

  /** Defaults matching the legacy Java constants in {@code WeaponsSystem} / {@code ConsumableSystem}. */
  public static final WeaponsConfig DEFAULTS =
      new WeaponsConfig(
          BulletConfig.DEFAULTS,
          BombConfig.DEFAULTS,
          GravBombConfig.DEFAULTS,
          MineConfig.DEFAULTS,
          ThorConfig.DEFAULTS,
          BurstFireConfig.DEFAULTS,
          RepelConfig.DEFAULTS);
}
