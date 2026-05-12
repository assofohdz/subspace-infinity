// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena decoy tuning ({@code DecoyAliveTime} cs×10); see REFERENCE.md {@code ## Misc}. */
public record DecoyConfig(long aliveTimeMs) {

  /** Subspace-canonical baseline ({@code DecoyAliveTime 30000ms}). */
  public static final DecoyConfig DEFAULTS = new DecoyConfig(30_000L);
}
