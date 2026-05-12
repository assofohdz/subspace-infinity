// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Upgradeable stat triple — mirrors Subspace's {@code InitialX} / {@code MaximumX} / {@code UpgradeX}. */
public record ShipStat(int initial, int max, int upgrade) {}
