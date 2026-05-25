// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

import infinity.Ship;

/**
 * Normalized read of a ship's effective {@code ShipConfig}: the input to the synergy table that
 * derives behaviour weights. Continuous dims are {@code [0,1]} against the arena's
 * {@link ArenaCapabilityNorms}; gates are capability booleans; inventory counts are raw. Derived
 * server-side (see {@code docs/bot-ai/capability-derivation.md}). See ADR-0014.
 */
public record CapabilityProfile(
    Ship ship,
    double mobility,
    double burstDamage,
    double sustainedDamage,
    double areaDamage,
    double tankiness,
    double rechargeEconomy,
    double rangeProfile,
    boolean bulletBounce,
    boolean bombBounce,
    boolean stealth,
    boolean cloak,
    boolean xRadar,
    boolean antiwarp,
    boolean attachReceive,
    int maxMines,
    int maxRepels,
    int maxBursts,
    int maxDecoys,
    int maxPortals,
    int maxThors,
    int maxBricks) {}
