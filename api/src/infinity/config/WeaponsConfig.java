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
 */
public record WeaponsConfig(
    BulletConfig bullet,
    BombConfig bomb,
    GravBombConfig gravBomb,
    MineConfig mine,
    ThorConfig thor,
    BurstFireConfig burst) {

  /** Defaults matching the legacy Java constants in {@code WeaponsSystem} / {@code ConsumableSystem}. */
  public static final WeaponsConfig DEFAULTS =
      new WeaponsConfig(
          BulletConfig.DEFAULTS,
          BombConfig.DEFAULTS,
          GravBombConfig.DEFAULTS,
          MineConfig.DEFAULTS,
          ThorConfig.DEFAULTS,
          BurstFireConfig.DEFAULTS);
}
