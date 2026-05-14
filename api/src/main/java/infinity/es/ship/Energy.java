// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Live energy pool; depletes on fire/damage, refills via {@link EnergyStats#rechargePerSecond()}; zero ⇒ death. Mutated by {@code EnergySystem}. */
public class Energy implements EntityComponent {

    private final int value;

    public Energy() {
        this(0);
    }

    public Energy(final int energy) {
        this.value = energy;
    }

    public int getEnergy() {
        return value;
    }

    public Energy newAdjusted(final int delta) {
        return new Energy(value + delta);
    }

    @Override
    public String toString() {
        return "Energy[" + value + "]";
    }
}
