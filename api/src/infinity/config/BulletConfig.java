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
 * Per-arena bullet projectile tuning. Read at projectile-creation time by
 * {@code WeaponsSystem.createProjectileGun} via the attacker's
 * {@code ArenaId} → {@link infinity.settings.ConfigRegistry#weapons()}.
 *
 * <p>Populated from the merged Groovy fragment store at arena-load — see
 * {@code GroovyWeaponsLoader}. Subspace fragment keys:
 * <ul>
 *   <li>{@code [Bullet] BulletDamageLevel} → {@link #damage}
 *   <li>{@code [Bullet] BulletAliveTime} (centiseconds) × 10 → {@link #decayMs}
 * </ul>
 *
 * @param damage damage applied on hit
 * @param decayMs lifetime in milliseconds before the projectile expires
 */
public record BulletConfig(int damage, long decayMs) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Pulled from the {@code svs} preset's {@code [Bullet]} section
   * ({@code BulletDamageLevel 100}, {@code BulletAliveTime 550}).
   */
  public static final BulletConfig DEFAULTS = new BulletConfig(100, 5500L);
}
