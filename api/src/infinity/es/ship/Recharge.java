// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>current effective recharge rate</b> (energy units per second).
 * EnergySystem reads this each tick to top up {@link Energy} until it hits
 * {@link EnergyMax}.
 *
 * <p>Maps to Subspace {@code [Ship] InitialRecharge + n*UpgradeRecharge},
 * clamped at {@link RechargeMax}. Mutated by upgrade-prize pickups. The
 * Subspace integer ({@code "amount per 10 seconds"}) is converted to per-sec
 * at projection time by ShipSpawnSystem.
 *
 * @author Asser
 */
public class Recharge implements EntityComponent {

    private final double rechargePerSecond;

    public Recharge() {
        this(0.0);
    }

    public Recharge(final double rechargePerSecond) {
        this.rechargePerSecond = rechargePerSecond;
    }

    public double getRechargePerSecond() {
        return rechargePerSecond;
    }

    public Recharge newAdjusted(final double delta) {
        return new Recharge(rechargePerSecond + delta);
    }

    @Override
    public String toString() {
        return "Recharge[" + rechargePerSecond + "]";
    }
}
