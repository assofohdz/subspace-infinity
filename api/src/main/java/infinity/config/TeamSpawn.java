// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * One team's spawn point. Subspace canonical encoding is the
 * {@code Team<N>-X / Team<N>-Y / Team<N>-Radius} triplet from the
 * {@code [Spawn]} section (REFERENCE.md {@code ## Spawn}); Infinity
 * stores them as an immutable record.
 *
 * <p>Coordinates are <em>arena-local tiles</em>: {@code (0, 0)} = NW
 * corner, {@code (1024, 1024)} = SE corner. The consumer
 * ({@code ArenaSystem.getArenaSpawn}) translates to world-space via
 * {@code ArenaSystem.arenaToWorld}.
 *
 * @param x arena-local X (tiles)
 * @param y arena-local Y (tiles) — Subspace's vertical axis maps to
 *     Infinity's Z; the field stays named {@code y} to match REFERENCE.md
 *     and Subspace fragment authoring conventions
 * @param radiusTiles spawn-circle radius in tiles ({@code 0} = exact
 *     point spawn; positive values randomize within a disc of this
 *     radius around {@code (x, y)})
 */
public record TeamSpawn(int x, int y, int radiusTiles) {}
