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
// spawnRadius is the disc radius (tiles) around the legacy single-spawn
// coord; only applied when no `team` entries are authored. 0 = exact
// point spawn (current behaviour). Diverges from Subspace canon
// [Misc] WarpRadiusLimit (arena-center anchor) — Infinity anchors on
// the arena.groovy-declared coord.

spawn {
    spawnRadius 0
    team x: 1000, y: 20, radius: 0   // freq 0 (and 4, 8, …)
}
