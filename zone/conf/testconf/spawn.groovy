// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// testconf [Spawn] tuning. Read by SpawnAdapter into SpawnConfig;
// consumed by ArenaSystem.getArenaSpawn(arenaName, freq) at connect-time
// (GameSessionHostedService.resolveInitialSpawn) and on in-arena
// ship-change / respawn (AvatarSystem.requestShipChange).
//
// Coordinates are arena-local tiles: (0, 0) = NW corner,
// (1024, 1024) = SE corner. radius=0 = exact point spawn.
//
// testarena spawns at arena centre (512, 512) — simplest testbed
// position; a single team is fine since freq lookup wraps via
// freq % teams.size() (REFERENCE.md ## Spawn).

spawn {
    spawnRadius 0
    team x: 512, y: 512, radius: 0
}
