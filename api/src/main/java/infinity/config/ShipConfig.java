// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.Ship;
import javax.annotation.Nullable;

/**
 * Per-ship tuning template; projected at spawn into ECS components by {@code ShipSpawnSystem}. See {@code config-pattern.md}.
 *
 * <p>Nullable weapon/inventory fields = ship cannot carry that gear; the spawn system skips the projection
 * block and the absent component reads as "not allowed" by prize appliers (component-absence as disallow signal).
 *
 * <p>{@code linearDamping} is an Infinity divergence — Subspace canon has no drag. mphys applies
 * {@code velocity *= pow(damping, t)} per tick; {@code 1.0} = no damping, {@code 0.99} ≈ 1%/sec loss.
 * {@code repellable} defaults to {@code true} per Subspace canon (repels push every ship).
 */
public record ShipConfig(
    Ship type,
    ShipStat rotation,
    ShipStat thrust,
    ShipStat speed,
    ShipStat recharge,
    ShipStat energy,
    double linearDamping,
    double turnResponsiveness,
    double bounceRestitution,
    double radarRange,
    @Nullable BombStats bombs,
    @Nullable CountWithDelayStats gravBombs,
    @Nullable BulletStats bullets,
    @Nullable MineStats mines,
    @Nullable BurstStats bursts,
    @Nullable CountWithDelayStats thors,
    @Nullable CountStats repels,
    @Nullable CountStats decoys,
    @Nullable CountStats bricks,
    @Nullable RocketStats rockets,
    @Nullable CountStats portals,
    @Nullable StatusStats cloak,
    @Nullable StatusStats stealth,
    @Nullable StatusStats xradar,
    @Nullable StatusStats antiwarp,
    boolean repellable) {}
