/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.settings;

import infinity.config.BombConfig;
import infinity.config.BulletConfig;
import infinity.config.BurstFireConfig;
import infinity.config.GravBombConfig;
import infinity.config.MineConfig;
import infinity.config.ThorConfig;
import infinity.config.WeaponsConfig;
import infinity.systems.SettingsSystem;

/**
 * Phase B bridge: derive a {@link WeaponsConfig} from the per-arena merged
 * Groovy fragment store ({@link SettingsSystem}). Today only the
 * {@code [Bullet]} section is wired; the other weapon sub-records keep their
 * {@link BulletConfig#DEFAULTS}-style defaults until each cluster's section
 * is wired in turn (see {@code .scratch/refactor-backlog/BACKLOG.md}).
 *
 * <p>Subspace fragment-key conventions used here:
 * <ul>
 *   <li>{@code BulletDamageLevel} — base damage on hit (raw integer, no scale).
 *   <li>{@code BulletAliveTime} — projectile lifetime in <em>centiseconds</em>
 *       (1cs = 10ms), per Subspace VIE convention. Multiplied by 10 to get ms.
 * </ul>
 */
public final class GroovyWeaponsLoader {

  /** {@code Bullet} section name in the merged fragment store. */
  static final String BULLET_SECTION = "Bullet";

  /**
   * Build a {@link WeaponsConfig} for {@code arenaName}. Keys missing from the
   * merged store fall back to the matching sub-record's {@code DEFAULTS}.
   */
  public WeaponsConfig load(final SettingsSystem settings, final String arenaName) {
    final BulletConfig bullet = loadBullet(settings, arenaName);
    return new WeaponsConfig(
        bullet,
        BombConfig.DEFAULTS,
        GravBombConfig.DEFAULTS,
        MineConfig.DEFAULTS,
        ThorConfig.DEFAULTS,
        BurstFireConfig.DEFAULTS);
  }

  private BulletConfig loadBullet(final SettingsSystem settings, final String arenaName) {
    final int damage =
        settings.getInt(
            arenaName, BULLET_SECTION, "BulletDamageLevel", BulletConfig.DEFAULTS.damage());
    final int aliveCs =
        settings.getInt(
            arenaName,
            BULLET_SECTION,
            "BulletAliveTime",
            (int) (BulletConfig.DEFAULTS.decayMs() / 10L));
    return new BulletConfig(damage, aliveCs * 10L);
  }
}
