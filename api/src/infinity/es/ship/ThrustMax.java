// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Ceiling on {@link Thrust} — the upgrade pickup system clamps at this value:
 * {@code Thrust = min(Thrust + ThrustUpgrade, ThrustMax)}.
 *
 * <p>Maps to Subspace {@code [Ship] MaximumThrust}. Per-entity so power-ups
 * can raise the ceiling for a single ship; typically left at the template
 * value otherwise.
 *
 * @author Asser Fahrenholz
 */
public class ThrustMax implements EntityComponent {

    private final int thrustMax;

    public ThrustMax() {
        this(0);
    }

    public ThrustMax(final int thrustMax) {
        this.thrustMax = thrustMax;
    }

    public int getThrustMax() {
        return thrustMax;
    }

    public ThrustMax newAdjusted(final int delta) {
        return new ThrustMax(thrustMax + delta);
    }

    @Override
    public String toString() {
        return "ThrustMax[" + thrustMax + "]";
    }
}
