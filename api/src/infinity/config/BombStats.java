// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BombLevel;

/**
 * Per-ship bomb tuning template. Projected at spawn into
 * {@code BombCurrentLevel} / {@code BombMaxLevel} / {@code BombCost} /
 * {@code BombFireDelay} components by {@code ShipSpawnSystem}.
 *
 * @param start initial bomb level a freshly-spawned ship has equipped
 * @param max highest bomb level the ship can ever reach (cap on level-up
 *     prizes)
 * @param cost energy cost per bomb fire
 * @param fireDelayCs cooldown between bomb fires, in centiseconds
 */
public record BombStats(BombLevel start, BombLevel max, int cost, long fireDelayCs) {}
