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
