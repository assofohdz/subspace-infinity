// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

/**
 * Per-arena maxima used to normalize each {@link CapabilityProfile} continuous dimension to
 * {@code [0,1]}. Computed across the arena's ships; recomputed on arena load + arena.groovy
 * reload. Arena-scoped (not zone) — same Spider is "fast" in a slow arena, "slow" in a fast one.
 * See ADR-0014.
 */
public record ArenaCapabilityNorms(
    double maxSpeed,
    double maxRotation,
    double maxThrust,
    double maxEnergy,
    double maxRecharge,
    double maxBurstDpsRaw,
    double maxSustainedDpsRaw,
    double maxAreaDamageRaw,
    double maxRange,
    double maxRechargeEconomy) {}
