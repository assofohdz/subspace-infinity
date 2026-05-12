// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.Ship;
import javax.annotation.Nullable;

/** Per-ship tuning template; projected at spawn into ECS components by {@code ShipSpawnSystem}. Nullable weapon/inventory fields = ship can't carry that gear. See {@code config-pattern.md}. */
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
