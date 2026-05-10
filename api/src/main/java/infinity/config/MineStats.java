// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BombLevel;

/**
 * Per-ship mine tuning template. Projected at spawn into
 * {@code MineCurrentLevel} / {@code MineMaxLevel} / {@code MineCost} /
 * {@code MineFireDelay} / {@code MineSpeed} components by
 * {@code ShipSpawnSystem}. Mines reuse the {@link BombLevel} enum for level
 * since they share the bomb-level numbering scheme.
 *
 * @param start initial mine level a freshly-spawned ship has equipped
 * @param max highest mine level the ship can ever reach
 * @param cost energy cost per mine drop
 * @param fireDelayCs cooldown between mine drops, in centiseconds
 * @param speed mine launch speed in <em>Subspace velocity units</em>;
 *     converted at fire time via {@code EngineConfig.subspaceVelocityScale}
 *     × clamp to {@code EngineConfig.maxProjectileSpeedJme}. Default
 *     {@code 0} = inert drop (mine drops dead-still — does not inherit
 *     ship velocity), which matches the historical behaviour of the
 *     velocity-zero special-case the audit flagged in F6. Slice s7-mine-speed
 *     replaces the special-case with this Pattern 4 knob so arenas can opt
 *     in to "kicker mines" by authoring a non-zero value.
 *
 *     <p><b>Infinity extension:</b> Subspace canon {@code ## Mine} section
 *     in REFERENCE.md does not define a per-ship {@code MineSpeed} knob; only
 *     {@code MineAliveTime} and {@code TeamMaxMines}. Per-ship {@code *Speed}
 *     keys in canon are {@code BulletSpeed} / {@code BombSpeed} /
 *     {@code BurstSpeed} only. This field opt-in lets arenas author kicker
 *     mines without breaking the canonical inert-drop default.
 */
public record MineStats(
    BombLevel start, BombLevel max, int cost, long fireDelayCs, int speed) {}
