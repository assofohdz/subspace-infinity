// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena Rocket buff thrust/speed overrides. {@code RocketThrust} / {@code RocketSpeed} per REFERENCE.md {@code ## Rocket}. */
public record RocketConfig(int thrust, int speed) {

  /** Subspace canon defaults ({@code RocketThrust 100}, {@code RocketSpeed 3000}). */
  public static final RocketConfig DEFAULTS = new RocketConfig(100, 3000);
}
