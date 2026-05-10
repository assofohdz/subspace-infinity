// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Stackable inventory counter — starting count plus hard cap. Used for
 * ship-action items that have no per-use cooldown attached to the cap
 * (bursts, repels). Items that also need a fire-delay cap use
 * {@link CountWithDelayStats} instead.
 *
 * @param start initial count a freshly-spawned ship has in inventory
 * @param max hard cap on the count (prizes can't push the inventory above
 *     this)
 */
public record CountStats(int start, int max) {}
