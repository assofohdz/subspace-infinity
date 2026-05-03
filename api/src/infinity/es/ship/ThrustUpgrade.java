// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Per-pickup increment added to {@link Thrust} when a 'Thruster' prize is
 * collected, clamped at {@link ThrustMax}.
 *
 * <p>Maps to Subspace {@code [Ship] UpgradeThrust}.
 *
 * @author Asser Fahrenholz
 */
public class ThrustUpgrade implements EntityComponent {

    private final int thrustUpgrade;

    public ThrustUpgrade() {
        this(0);
    }

    public ThrustUpgrade(final int thrustUpgrade) {
        this.thrustUpgrade = thrustUpgrade;
    }

    public int getThrustUpgrade() {
        return thrustUpgrade;
    }

    public ThrustUpgrade newAdjusted(final int delta) {
        return new ThrustUpgrade(thrustUpgrade + delta);
    }

    @Override
    public String toString() {
        return "ThrustUpgrade[" + thrustUpgrade + "]";
    }
}
