// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>absolute hard cap on {@link Energy}</b> — the value the
 * upgradeable {@link Energy} cap can never exceed regardless of how many
 * ENERGY prizes are picked up. Same role as {@link ThrustMax}/{@link SpeedMax}/etc.
 *
 * <p>Maps to Subspace {@code [Ship] MaximumEnergy}. Read by
 * {@code PrizeSystem.handleAcquireEnergy} to clamp the post-upgrade
 * {@link Energy} value; never mutated at runtime.
 *
 * @author Asser Fahrenholz
 */
public class EnergyMax implements EntityComponent {

    private final int max;

    public EnergyMax() {
        this(0);
    }

    public EnergyMax(final int max) {
        this.max = max;
    }

    public int getMaxEnergy() {
        return max;
    }

    public EnergyMax newAdjusted(final int delta) {
        return new EnergyMax(max + delta);
    }

    @Override
    public String toString() {
        return "EnergyMax[" + max + "]";
    }
}
