// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena portal tuning ({@code WarpPointDelay} cs×10); see REFERENCE.md {@code ## Misc}. */
public record PortalConfig(long activeTimeMs) {

  /** Subspace-canonical baseline ({@code WarpPointDelay 60000ms}). */
  public static final PortalConfig DEFAULTS = new PortalConfig(60_000L);
}
