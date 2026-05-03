// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena decoy tuning. Read at place-time by
 * {@code ConsumableSystem.createDecoy} via the placing ship's
 * {@code ArenaId} → {@link infinity.settings.ConfigRegistry#decoy()} and
 * projected onto the spawned decoy marker entity as
 * {@link com.simsilica.es.common.Decay} (deadline = createdTime +
 * {@link #aliveTimeMs}).
 *
 * <p>Subspace fragment keys (REFERENCE.md {@code ## Misc}):
 * <ul>
 *   <li>{@code [Misc] DecoyAliveTime} (centiseconds) × 10 → {@link #aliveTimeMs}
 * </ul>
 *
 * <p>Slice 4 ships plumbing only — the marker entity carries the decay
 * deadline but no shape, no radar visibility, no fake-ship behaviour. The
 * follow-up "decoy as radar fake" slice will read from this template at
 * spawn time to drive the canonical Subspace mechanic (a phantom ship on
 * enemy radar that mimics the placer's heading).
 *
 * @param aliveTimeMs decoy lifetime in milliseconds (Subspace
 *     {@code DecoyAliveTime} converted from centiseconds)
 */
public record DecoyConfig(long aliveTimeMs) {

  /**
   * Subspace-canonical baseline pulled from the {@code base} preset's
   * {@code [Misc] DecoyAliveTime 3000} cs = {@code 30000} ms.
   */
  public static final DecoyConfig DEFAULTS = new DecoyConfig(30_000L);
}
