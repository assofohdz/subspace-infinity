// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena Thor projectile tuning; populated from {@code thor.groovy} via {@code ThorAdapter}.
 * Infinity divergence — no Subspace canon ({@code [Thor]} section does not exist in REFERENCE.md).
 */
public record ThorConfig(int damage, long decayMs, int launchVelocity) {

  public static final ThorConfig DEFAULTS = new ThorConfig(10, 1500L, 50);
}
