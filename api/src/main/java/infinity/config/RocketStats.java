// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-ship rocket inventory + buff lifetime (Subspace {@code RocketTime}); distinct from arena-global thrust/speed in {@link RocketConfig}. */
public record RocketStats(int start, int max, long activeTimeCs) {}
