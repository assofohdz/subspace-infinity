// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>current effective energy cap</b>: the value the live
 * {@link Health} pool tops out at when recharging, and the upgradeable axis
 * the ENERGY prize bumps via {@link EnergyUpgrade}, hard-clamped at
 * {@link EnergyMax}. Same role as {@link Thrust}/{@link Speed}/etc. for the
 * energy pool.
 *
 * <p>Maps to Subspace's running {@code MaximumEnergy} (which grows from
 * {@code InitialEnergy} toward {@code MaximumEnergy} via energy prizes).
 * Mutated by upgrade-prize pickups, NOT by damage — damage and recharge
 * mutate {@link Health}, not this.
 *
 * @author Asser Fahrenholz
 */
public class Energy implements EntityComponent {

    private final int energy;

    public Energy() {
        this(0);
    }

    public Energy(final int energy) {
        this.energy = energy;
    }

    public int getEnergy() {
        return energy;
    }

    public Energy newAdjusted(final int delta) {
        return new Energy(energy + delta);
    }

    @Override
    public String toString() {
        return "Energy[" + energy + "]";
    }
}
