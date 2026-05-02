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
 * Per-arena prize-spawn defaults. Read by {@code PrizeSystem} (and by
 * arena-load logic when materialising prize-spawner specs) and forwarded to
 * the {@code GameEntities} factories — those factories keep matching
 * constants ({@code PRIZE_DEFAULT_DECAY_MS} / {@code PRIZE_DEFAULT_MAX_COUNT}
 * / {@code BOUNTY_VALUE}) as last-resort fallbacks so module authors using
 * the api can call them without a config lookup, but the per-arena values
 * here are authoritative on the server hot path.
 *
 * @param defaultDecayMs default lifetime in ms for prizes that don't
 *     specify per-spawner ttl (legacy default {@code 20000})
 * @param defaultMaxCount default simultaneous-prize cap for spawners that
 *     don't specify their own (legacy default {@code 10})
 * @param bountyValue greens granted to a ship picking up a prize (legacy
 *     default {@code 10})
 */
public record PrizeConfig(long defaultDecayMs, int defaultMaxCount, int bountyValue) {

  public static final PrizeConfig DEFAULTS = new PrizeConfig(20000L, 10, 10);
}
