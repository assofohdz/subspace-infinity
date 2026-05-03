// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.GunLevel;

/**
 * Per-ship gun tuning template. Projected at spawn into
 * {@code GunCurrentLevel} / {@code GunMaxLevel} / {@code GunCost} /
 * {@code GunFireDelay} components by {@code ShipSpawnSystem}.
 *
 * @param start initial gun level a freshly-spawned ship has equipped
 * @param max highest gun level the ship can ever reach (cap on level-up
 *     prizes)
 * @param cost energy cost per gun fire
 * @param fireDelayCs cooldown between gun fires, in centiseconds
 */
public record GunStats(GunLevel start, GunLevel max, int cost, long fireDelayCs) {}
