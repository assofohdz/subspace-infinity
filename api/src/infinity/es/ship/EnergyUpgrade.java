// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Per-pickup increment added to {@link EnergyMax} (NOT live {@link Energy})
 * when an energy upgrade prize is collected. Raises the ship's max HP cap;
 * the live HP gauge is unaffected until the next recharge tick.
 *
 * <p>Maps to Subspace {@code [Ship] UpgradeEnergy}.
 *
 * @author Asser Fahrenholz
 */
public class EnergyUpgrade implements EntityComponent {

    private final int energyUpgrade;

    public EnergyUpgrade() {
        this(0);
    }

    public EnergyUpgrade(final int energyUpgrade) {
        this.energyUpgrade = energyUpgrade;
    }

    public int getEnergyUpgrade() {
        return energyUpgrade;
    }

    public EnergyUpgrade newAdjusted(final int delta) {
        return new EnergyUpgrade(energyUpgrade + delta);
    }

    @Override
    public String toString() {
        return "EnergyUpgrade[" + energyUpgrade + "]";
    }
}
