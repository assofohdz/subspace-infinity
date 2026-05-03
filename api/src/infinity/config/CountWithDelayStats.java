// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Stackable inventory counter that also carries a per-use cooldown — used
 * for thors. Bursts and repels have no fire delay and use {@link CountStats}
 * instead.
 *
 * @param start initial count a freshly-spawned ship has in inventory
 * @param max hard cap on the count
 * @param fireDelayCs cooldown between uses, in centiseconds
 */
public record CountWithDelayStats(int start, int max, long fireDelayCs) {}
