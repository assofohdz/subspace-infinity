// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena Rocket buff tuning. Read at fire-time by
 * {@code ConsumableSystem} via the attacker's {@code ArenaId} →
 * {@link infinity.settings.ConfigRegistry#rocket()} and projected onto
 * the spawned rocket-buff entity (via Pattern 4) as the override
 * thrust/speed values applied to the ship for the buff's lifetime.
 *
 * <p>Subspace fragment keys (REFERENCE.md {@code ## Rocket}):
 * <ul>
 *   <li>{@code [Rocket] RocketThrust} → {@link #thrust} (overrides
 *       ship's {@code Thrust} while rocket active)
 *   <li>{@code [Rocket] RocketSpeed} → {@link #speed} (overrides
 *       ship's {@code Speed} while rocket active)
 * </ul>
 *
 * <p>The buff's lifetime ({@code RocketTime}) is per-ship — see
 * {@link infinity.es.ship.actions.RocketTime}.
 *
 * @param thrust thrust value applied to the ship while rocket active
 * @param speed top speed applied to the ship while rocket active
 */
public record RocketConfig(int thrust, int speed) {

  /**
   * Subspace-canonical baseline pulled from the {@code base} preset's
   * {@code [Rocket]} section ({@code RocketThrust 100},
   * {@code RocketSpeed 3000}).
   */
  public static final RocketConfig DEFAULTS = new RocketConfig(100, 3000);
}
