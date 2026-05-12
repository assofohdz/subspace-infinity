// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-ship Status-family ({@code Cloak} / {@code Stealth} / {@code XRadar} / {@code AntiWarp}) template — tri-state {@code *Status} + raw {@code *Energy} drain. See REFERENCE.md "Ship abilities". */
public record StatusStats(int status, int energyDrainPer1000Cs) {}
