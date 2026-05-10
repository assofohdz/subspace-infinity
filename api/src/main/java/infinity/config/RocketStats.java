// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-ship rocket inventory + active-buff duration. Mirrors
 * {@link CountWithDelayStats}'s shape but the third field is buff
 * lifetime (Subspace {@code RocketTime}) rather than fire cooldown.
 *
 * <p>{@link CountStats} alone can't carry this — the activation buff
 * needs a per-ship duration sourced from the per-ship Subspace key
 * {@code RocketTime} (centiseconds), distinct from the arena-global
 * {@link RocketConfig} thrust/speed override values.
 *
 * @param start initial inventory count a freshly-spawned ship has
 * @param max hard inventory cap
 * @param activeTimeCs buff lifetime in centiseconds (Subspace
 *     {@code RocketTime}; converted to ms at projection time when
 *     building the buff entity's {@code Decay} deadline)
 */
public record RocketStats(int start, int max, long activeTimeCs) {}
