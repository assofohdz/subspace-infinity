// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-ship Status-family ({@code Cloak} / {@code Stealth} / {@code XRadar} / {@code AntiWarp}) template. See REFERENCE.md "Ship abilities".
 *
 * <p>{@code status} is Subspace's tri-state {@code *Status}: 0=forbidden, 1=acquirable, 2=acquirable+spawn-active.
 * {@code energyDrainPer1000Cs} is raw {@code *Energy} (0..32000); drain/cs = {@code value/1000}.
 */
public record StatusStats(int status, int energyDrainPer1000Cs) {}
