// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena portal tuning. Read at place-time by
 * {@code ConsumableSystem.createPortal} via the placing ship's
 * {@code ArenaId} → {@link infinity.settings.ConfigRegistry#portal()} and
 * projected onto the spawned portal marker entity as
 * {@link com.simsilica.es.common.Decay} (deadline = createdTime +
 * {@link #activeTimeMs}).
 *
 * <p>Subspace fragment keys (REFERENCE.md {@code ## Misc}):
 * <ul>
 *   <li>{@code [Misc] WarpPointDelay} (centiseconds) × 10 → {@link #activeTimeMs}
 * </ul>
 *
 * <p>Slice 5 ships plumbing only — the marker entity carries the decay
 * deadline + parent linkage. The follow-up "warp to placed portal"
 * action slice will read this template at warp-time to drive the
 * canonical Subspace mechanic.
 *
 * @param activeTimeMs portal lifetime in milliseconds (Subspace
 *     {@code WarpPointDelay} converted from centiseconds)
 */
public record PortalConfig(long activeTimeMs) {

  /**
   * Subspace-canonical baseline pulled from the {@code base} preset's
   * {@code [Misc] WarpPointDelay 6000} cs = {@code 60000} ms.
   */
  public static final PortalConfig DEFAULTS = new PortalConfig(60_000L);
}
