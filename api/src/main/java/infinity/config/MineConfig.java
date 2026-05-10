// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena mine projectile tuning. Damage is currently sourced from the
 * per-ship {@code MineCost} component (Subspace's "mine cost = damage")
 * convention, so this record only carries decay; if the convention is
 * later split (separate cost vs damage), add a {@code damage} field here.
 *
 * <p>Populated from the merged Groovy fragment store at arena-load — see
 * {@code GroovyWeaponsLoader}. Subspace fragment keys:
 * <ul>
 *   <li>{@code [Mine] MineAliveTime} (centiseconds) × 10 → {@link #decayMs}
 * </ul>
 *
 * @param decayMs mine lifetime in ms before auto-removal
 */
public record MineConfig(long decayMs) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Pulled from the {@code svs} preset's {@code [Mine]} section
   * ({@code MineAliveTime 12000} centiseconds = 120000ms = 2min).
   */
  public static final MineConfig DEFAULTS = new MineConfig(120000L);
}
