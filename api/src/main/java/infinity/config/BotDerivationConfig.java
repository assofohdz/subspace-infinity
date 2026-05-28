// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Engine-tier coefficients for the {@code CapabilityDeriver} (ADR-0014). Operator-tunable knobs that
 * shape how raw {@code ShipConfig} numbers normalize into a {@code CapabilityProfile} (and therefore
 * which behaviours a hull derives high). Authored in {@code zone/engine-bot-ai.groovy} via the
 * {@code derivation { }} block; loaded by {@code GroovyBotDerivationLoader}.
 *
 * <p>Area-damage coefficients are "level² × count proxies" used to compare radius² between weapon
 * families that don't share a unit (grav bomb vs burst vs thor); mobility blend weights let a tuner
 * de-emphasise one axis (e.g. {@code thrust = 0.0} for arenas where thrust doesn't matter).
 */
public record BotDerivationConfig(
    double gravBombArea,
    double burstArea,
    double thorArea,
    double sustainRechargeScale,
    double mobilitySpeedWeight,
    double mobilityRotationWeight,
    double mobilityThrustWeight) {

  /** Current Java values from {@code CapabilityDeriver} pre-migration; preserves behaviour when no config. */
  public static final BotDerivationConfig DEFAULTS =
      new BotDerivationConfig(9.0, 1.0, 16.0, 1000.0, 1.0, 1.0, 1.0);
}
