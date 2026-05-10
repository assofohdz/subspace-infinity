// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Upgradeable per-ship stat triple: the value a fresh ship spawns with, the
 * cap it can never exceed, and the per-upgrade increment.
 *
 * <p>Mirrors the Subspace tuning model where each relevant stat (thrust,
 * speed, rotation, recharge, energy, …) has an {@code InitialX} / {@code
 * MaximumX} / {@code UpgradeX} triple. The runtime per-entity components
 * ({@code Thrust} / {@code ThrustMax} / {@code ThrustUpgrade}) are derived
 * from this triple at spawn time.
 *
 * @param initial the value a freshly spawned ship starts with (also the value
 *     before any upgrades are applied)
 * @param max the hard cap; even after all upgrades, the effective value never
 *     exceeds this
 * @param upgrade the increment applied per upgrade pickup
 */
public record ShipStat(int initial, int max, int upgrade) {}
