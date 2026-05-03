// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BombLevel;

/**
 * Per-ship mine tuning template. Projected at spawn into
 * {@code MineCurrentLevel} / {@code MineMaxLevel} / {@code MineCost} /
 * {@code MineFireDelay} components by {@code ShipSpawnSystem}. Mines reuse
 * the {@link BombLevel} enum for level since they share the bomb-level numbering
 * scheme.
 *
 * @param start initial mine level a freshly-spawned ship has equipped
 * @param max highest mine level the ship can ever reach
 * @param cost energy cost per mine drop
 * @param fireDelayCs cooldown between mine drops, in centiseconds
 */
public record MineStats(BombLevel start, BombLevel max, int cost, long fireDelayCs) {}
