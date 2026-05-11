// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Bundled Stats record for the Energy aspect — the slowly-changing rules
 * that govern the live {@link Energy} pool. Replaces today's six scattered
 * cap/recharge components ({@code Energy} (cap), {@code EnergyMax},
 * {@code EnergyUpgrade}, {@code Recharge}, {@code RechargeMax},
 * {@code RechargeUpgrade}) per the ADR 0001 Continuous + Stats split.
 *
 * <p>Pattern 4 (template→component) projection at spawn time: the values
 * here are projected from {@code ShipConfig.energy()} +
 * {@code ShipConfig.recharge()} by {@code ShipSpawnSystem} in a single
 * {@code setComponent} call. Runtime mutation (prize-pickup cap bumps,
 * recharge bumps) flows through {@code EnergyStatsChange} drained by
 * {@code EnergyStatsSystem} — see
 * {@link infinity.es.ChangeTarget} and ADR 0001 for the canonical-writer
 * shape.
 *
 * <p>Field naming + unit conventions:
 * <ul>
 *   <li>{@code max} — current effective energy cap (the live pool tops
 *       out here). Subspace canon: per-prize-bumpable
 *       {@code InitialEnergy + n*UpgradeEnergy}, clamped at
 *       {@code hardMax}. Was today's {@code Energy} component.
 *   <li>{@code hardMax} — absolute ceiling on {@code max}; runtime-
 *       immutable, set at spawn projection. Subspace canon
 *       {@code MaximumEnergy}. Was today's {@code EnergyMax} component.
 *   <li>{@code upgrade} — per-prize-pickup increment added to
 *       {@code max} when an ENERGY prize is collected. Subspace canon
 *       {@code UpgradeEnergy}. Was today's {@code EnergyUpgrade}.
 *   <li>{@code rechargePerSecond} — current effective recharge rate
 *       (energy units per second). Was today's {@code Recharge}
 *       component. Subspace canon: per-prize-bumpable
 *       {@code InitialRecharge / 10 + n*UpgradeRecharge / 10} (Subspace
 *       integer is "amount per 10 seconds", converted at spawn).
 *   <li>{@code rechargeMax} — ceiling on {@code rechargePerSecond};
 *       runtime-immutable, set at spawn. Was today's {@code RechargeMax}.
 *       Subspace canon {@code MaximumRecharge / 10}.
 *   <li>{@code rechargeUpgrade} — per-prize-pickup increment for
 *       {@code rechargePerSecond}. Was today's {@code RechargeUpgrade}.
 *       Subspace canon {@code UpgradeRecharge / 10}.
 * </ul>
 *
 * <p><b>Canonical writer:</b> {@code EnergyStatsSystem} (drains
 * {@code EnergyStatsChange} + {@link infinity.es.ChangeTarget} entities,
 * folds same-tick deltas additively per ship, clamps {@code max} at
 * {@code hardMax} and {@code rechargePerSecond} at {@code rechargeMax}).
 * {@code ShipSpawnSystem} is the spawn-time projector (factory tier).
 *
 * @param max                  current effective energy cap (live pool
 *                             {@link Energy} tops out here)
 * @param hardMax              absolute hard cap on {@code max}
 * @param upgrade              per-pickup {@code max} increment
 * @param rechargePerSecond    current effective recharge rate (energy/sec)
 * @param rechargeMax          ceiling on {@code rechargePerSecond}
 * @param rechargeUpgrade      per-pickup {@code rechargePerSecond} increment
 * @author Asser Fahrenholz
 */
public record EnergyStats(
    int max,
    int hardMax,
    int upgrade,
    double rechargePerSecond,
    double rechargeMax,
    double rechargeUpgrade)
    implements EntityComponent {

  /**
   * No-arg constructor required by {@code .claude/rules/components.md}
   * for Zay-ES deserialization symmetry. Delegates to the canonical
   * record constructor with zero arguments.
   */
  public EnergyStats() {
    this(0, 0, 0, 0.0, 0.0, 0.0);
  }
}
