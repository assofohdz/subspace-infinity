// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** One team's spawn point ({@code Team<N>-X/Y/Radius}) in arena-local tiles; {@code y} = Subspace vertical = Infinity Z. See REFERENCE.md {@code ## Spawn}. */
public record TeamSpawn(int x, int y, int radiusTiles) {}
