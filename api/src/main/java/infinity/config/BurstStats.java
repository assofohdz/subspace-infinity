// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-ship burst tuning template; {@code speed} is per-projectile in Subspace velocity units (positive magnitude only — burst is a 360° fan). Projectile count is arena-global on {@link BurstFireConfig}. */
public record BurstStats(int start, int max, int speed) {}
