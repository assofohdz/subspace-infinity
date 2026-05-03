// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.BombConfig;
import infinity.config.BulletConfig;
import infinity.config.BurstFireConfig;
import infinity.config.GravBombConfig;
import infinity.config.MineConfig;
import infinity.config.PrizeConfig;
import infinity.config.RepelConfig;
import infinity.config.ThorConfig;
import infinity.config.WeaponsConfig;
import infinity.systems.SettingsSystem;

/**
 * Phase B bridge: derive {@link WeaponsConfig} and {@link PrizeConfig} from
 * the per-arena merged Groovy fragment store ({@link SettingsSystem}).
 * See {@code .scratch/refactor-backlog/BACKLOG.md} for the cluster-by-cluster
 * status of which sections are wired vs. still on {@code DEFAULTS}.
 *
 * <p>Subspace fragment-key conventions used here:
 * <ul>
 *   <li>{@code *DamageLevel} — base damage on hit (raw integer, no scale).
 *   <li>{@code *AliveTime} / {@code PrizeMaxExist} — durations in
 *       <em>centiseconds</em> (1cs = 10ms), per Subspace VIE convention.
 *       Multiplied by 10 to get ms.
 * </ul>
 */
public final class GroovyWeaponsLoader {

  /** {@code Bullet} section name in the merged fragment store. */
  static final String BULLET_SECTION = "Bullet";

  /** {@code Bomb} section name in the merged fragment store. */
  static final String BOMB_SECTION = "Bomb";

  /** {@code Mine} section name in the merged fragment store. */
  static final String MINE_SECTION = "Mine";

  /** {@code Burst} section name in the merged fragment store. */
  static final String BURST_SECTION = "Burst";

  /** {@code Repel} section name in the merged fragment store. */
  static final String REPEL_SECTION = "Repel";

  /** {@code Prize} section name in the merged fragment store. */
  static final String PRIZE_SECTION = "Prize";

  /**
   * Build a {@link WeaponsConfig} for {@code arenaName}. Keys missing from the
   * merged store fall back to the matching sub-record's {@code DEFAULTS}.
   *
   * <p>{@code GravBombConfig} and {@code ThorConfig} have no Subspace
   * fragment section today (gravbombs share {@code [Bomb]} tuning in VIE,
   * Thors are an Infinity addition without a canonical section), so those
   * stay on {@code DEFAULTS}.
   */
  public WeaponsConfig load(final SettingsSystem settings, final String arenaName) {
    return new WeaponsConfig(
        loadBullet(settings, arenaName),
        loadBomb(settings, arenaName),
        GravBombConfig.DEFAULTS,
        loadMine(settings, arenaName),
        ThorConfig.DEFAULTS,
        loadBurst(settings, arenaName),
        loadRepel(settings, arenaName));
  }

  private BulletConfig loadBullet(final SettingsSystem settings, final String arenaName) {
    final int damage =
        settings.getInt(
            arenaName, BULLET_SECTION, "BulletDamageLevel", BulletConfig.DEFAULTS.damage());
    final int damageUpgrade =
        settings.getInt(
            arenaName,
            BULLET_SECTION,
            "BulletDamageUpgrade",
            BulletConfig.DEFAULTS.damageUpgrade());
    final int aliveCs =
        settings.getInt(
            arenaName,
            BULLET_SECTION,
            "BulletAliveTime",
            (int) (BulletConfig.DEFAULTS.decayMs() / 10L));
    return new BulletConfig(damage, damageUpgrade, aliveCs * 10L);
  }

  private BombConfig loadBomb(final SettingsSystem settings, final String arenaName) {
    final int damage =
        settings.getInt(
            arenaName, BOMB_SECTION, "BombDamageLevel", BombConfig.DEFAULTS.damage());
    final int aliveCs =
        settings.getInt(
            arenaName,
            BOMB_SECTION,
            "BombAliveTime",
            (int) (BombConfig.DEFAULTS.decayMs() / 10L));
    return new BombConfig(damage, aliveCs * 10L);
  }

  private MineConfig loadMine(final SettingsSystem settings, final String arenaName) {
    final int aliveCs =
        settings.getInt(
            arenaName,
            MINE_SECTION,
            "MineAliveTime",
            (int) (MineConfig.DEFAULTS.decayMs() / 10L));
    return new MineConfig(aliveCs * 10L);
  }

  private BurstFireConfig loadBurst(final SettingsSystem settings, final String arenaName) {
    final int damage =
        settings.getInt(
            arenaName, BURST_SECTION, "BurstDamageLevel", BurstFireConfig.DEFAULTS.damage());
    return new BurstFireConfig(
        BurstFireConfig.DEFAULTS.projectileCount(),
        BurstFireConfig.DEFAULTS.decayMs(),
        damage);
  }

  private RepelConfig loadRepel(final SettingsSystem settings, final String arenaName) {
    final int speed =
        settings.getInt(arenaName, REPEL_SECTION, "RepelSpeed", RepelConfig.DEFAULTS.speed());
    final int timeCs =
        settings.getInt(
            arenaName,
            REPEL_SECTION,
            "RepelTime",
            (int) (RepelConfig.DEFAULTS.timeMs() / 10L));
    final int distance =
        settings.getInt(
            arenaName, REPEL_SECTION, "RepelDistance", RepelConfig.DEFAULTS.distancePixels());
    return new RepelConfig(speed, timeCs * 10L, distance);
  }

  /**
   * Build a {@link PrizeConfig} for {@code arenaName}. Today only
   * {@code PrizeMaxExist} maps to an existing {@link PrizeConfig} field;
   * other Subspace {@code [Prize]} keys ({@code PrizeFactor},
   * {@code PrizeDelay}, {@code MultiPrizeCount}, …) need new
   * {@link PrizeConfig} fields + consumer wiring before they can be picked
   * up here.
   */
  public PrizeConfig loadPrize(final SettingsSystem settings, final String arenaName) {
    final int maxExistCs =
        settings.getInt(
            arenaName,
            PRIZE_SECTION,
            "PrizeMaxExist",
            (int) (PrizeConfig.DEFAULTS.defaultDecayMs() / 10L));
    return new PrizeConfig(
        maxExistCs * 10L,
        PrizeConfig.DEFAULTS.defaultMaxCount(),
        PrizeConfig.DEFAULTS.bountyValue());
  }
}
