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
 * Per-arena gravity-bomb (warp-bomb / wormhole-bomb) tuning. Read at
 * projectile-creation time by {@code WeaponsSystem.createProjectileGravBomb}.
 *
 * <p><b>Damage and decay are inherited from {@link BombConfig}.</b> Subspace
 * VIE treats gravity bombs as level-3 bombs sharing the {@code [Bomb]}
 * section's tuning; this record only carries the two Infinity-specific
 * knobs ({@link #delayMs}, {@link #wormholeForce}) that govern the
 * wormhole phase. There is no canonical {@code [GravBomb]} fragment
 * section.
 *
 * @param delayMs delay before the bomb transitions into a wormhole
 *     (Infinity default {@code 1000})
 * @param wormholeForce gravity-well pull strength once the bomb opens its
 *     wormhole phase (Infinity default {@code 5000})
 */
public record GravBombConfig(long delayMs, double wormholeForce) {

  public static final GravBombConfig DEFAULTS = new GravBombConfig(1000L, 5000.0);
}
