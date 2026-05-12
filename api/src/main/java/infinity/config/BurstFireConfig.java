// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena burst-firing tuning; distinct from per-ship inventory cap on {@link ShipConfig#bursts()}. See REFERENCE.md {@code ## Burst}. */
public record BurstFireConfig(long projectileCount, long decayMs, int damage) {

  /** Subspace-canonical baseline ({@code BurstDamageLevel 250}); count/decay are Infinity defaults. */
  public static final BurstFireConfig DEFAULTS = new BurstFireConfig(30L, 1500L, 250);
}
