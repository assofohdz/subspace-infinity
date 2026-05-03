// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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
 * <p>Populated from the typed {@code prize.groovy} fragment via
 * {@code PrizeAdapter}. Subspace fragment keys
 * (REFERENCE.md {@code ## Prize}):
 * <ul>
 *   <li>{@code [Prize] PrizeMaxExist} (centiseconds) × 10 → {@link #defaultDecayMs}
 *   <li>{@code [Prize] PrizeMinExist} (centiseconds) × 10 → {@link #defaultMinDecayMs}
 *   <li>{@code [Prize] DeathPrizeTime} (centiseconds) × 10 → {@link #deathPrizeTimeMs}
 *   <li>{@code [Prize] PrizeNegativeFactor} (int) → {@link #prizeNegativeFactor}
 * </ul>
 *
 * <p>{@link #defaultMinDecayMs} + {@link #defaultDecayMs} together define a
 * uniform random lifetime range — {@code PrizeSystem} samples in
 * {@code [defaultMinDecayMs, defaultDecayMs]} when a prize spawner has no
 * explicit per-spawner {@code ttlMs}. Arenas that don't author
 * {@code minExist} get {@code defaultMinDecayMs == defaultDecayMs}
 * (= no randomness; every prize lives exactly {@link #defaultDecayMs}),
 * preserving 1:1 behaviour for un-migrated arenas.
 *
 * <p>{@link #deathPrizeTimeMs} controls the lifetime of prizes spawned at
 * a ship's death point ({@code DeathSystem} → {@code PrizeSystem.spawnDeathPrize}).
 * Defaults to {@code 0L} = death-drops disabled; arenas opt in by authoring
 * {@code deathPrizeTime <cs>}. Threshold checks (e.g. ship-bounty gate)
 * are deferred to a follow-up slice; today every ship death within an
 * authored arena drops one weighted prize.
 *
 * <p>{@link #prizeNegativeFactor} is Subspace's 1-in-N "negative prize"
 * odds (REFERENCE.md: {@code 1=every prize, 32000=extremely rare}).
 * Slice 8c implements this via DUD substitution rather than building
 * the inverse-applier matrix — when the dice roll hits, the spawned
 * prize is replaced by a {@link infinity.es.PrizeTypes#DUD} (no-op
 * pickup) instead of running an inverse stat-degradation effect. The
 * config knob preserves the canonical probability semantics so a future
 * follow-up slice can swap the substitution for proper inverse
 * appliers without re-authoring presets. {@code 0} disables the roll
 * (no negatives).
 *
 * <p>{@link #defaultMaxCount} and {@link #bountyValue} have no canonical
 * Subspace fragment key today; they stay on their Infinity defaults until
 * a content-side decision wires them.
 *
 * @param defaultDecayMs upper bound of the random-lifetime range in ms
 *     (Subspace {@code PrizeMaxExist} converted from centiseconds);
 *     also the legacy "default decay" for spawners with no explicit
 *     {@code ttlMs} when {@code minExist} isn't authored
 * @param defaultMinDecayMs lower bound of the random-lifetime range in ms
 *     (Subspace {@code PrizeMinExist} converted from centiseconds);
 *     equals {@link #defaultDecayMs} when the arena's {@code prize.groovy}
 *     omits the {@code minExist} field
 * @param deathPrizeTimeMs lifetime in ms of prizes dropped at ship death
 *     (Subspace {@code DeathPrizeTime} converted from centiseconds);
 *     {@code 0L} disables death-drops entirely
 * @param prizeNegativeFactor 1-in-N odds for a spawning prize to be
 *     replaced by {@code Dud} (Subspace {@code PrizeNegativeFactor});
 *     {@code 0} disables the roll
 * @param defaultMaxCount default simultaneous-prize cap for spawners that
 *     don't specify their own (Infinity default {@code 10})
 * @param bountyValue greens granted to a ship picking up a prize (Infinity
 *     default {@code 10})
 */
public record PrizeConfig(
    long defaultDecayMs,
    long defaultMinDecayMs,
    long deathPrizeTimeMs,
    int prizeNegativeFactor,
    int defaultMaxCount,
    int bountyValue) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Decay pulled from the {@code svs} preset's {@code [Prize] PrizeMaxExist}
   * ({@code 8000} centiseconds = 80000 ms); {@link #defaultMinDecayMs}
   * defaults to the same value (= no randomness) until an arena authors
   * {@code minExist}. {@link #deathPrizeTimeMs} defaults to {@code 0L}
   * (death-drops disabled). {@link #prizeNegativeFactor} defaults to
   * {@code 0} (no negative-prize roll). Count and bounty keep Infinity
   * defaults — no canonical Subspace key for those.
   */
  public static final PrizeConfig DEFAULTS = new PrizeConfig(80_000L, 80_000L, 0L, 0, 10, 10);
}
