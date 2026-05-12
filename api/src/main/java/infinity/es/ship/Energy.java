// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Live energy pool; depletes on fire/damage, refills via {@link EnergyStats#rechargePerSecond()}; zero ⇒ death. Mutated by {@code EnergySystem}. */
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
