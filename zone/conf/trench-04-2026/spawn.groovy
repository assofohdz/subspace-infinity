// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Trench [Spawn] tuning. Read by SpawnAdapter into SpawnConfig;
// consumed by ArenaSystem.getArenaSpawn(arenaName, freq) at connect-time
// (GameSessionHostedService.resolveInitialSpawn) and on in-arena
// ship-change / respawn (AvatarSystem.requestShipChange).
//
// Coordinates are arena-local tiles: (0, 0) = NW corner,
// (1024, 1024) = SE corner. radius=0 = exact point spawn.
//
// Migrated 1:1 from trench-04-2026/arena.groovy `spawn 1000, 20` —
// authored as a single team (team0) so behaviour is unchanged. To split
// freqs across the map, add team1 / team2 / … entries; lookup wraps via
// freq % teams.size() (REFERENCE.md ## Spawn).
//
// warpRadiusLimit slot is reserved (REFERENCE.md [Misc]
// WarpRadiusLimit, "1024 = anywhere"); consumption deferred to the
// WarpSystem-randomization follow-up slice.

spawn {
    warpRadiusLimit  1024
    team x: 1000, y: 20, radius: 0   // freq 0 (and 4, 8, …)
}
