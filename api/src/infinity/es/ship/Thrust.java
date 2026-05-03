// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>current effective thrust rate</b> (acceleration units/sec).
 * This is the value PlayerDriver reads each tick to compute force.
 *
 * <p>Maps to Subspace {@code [Ship] InitialThrust + n*UpgradeThrust}, clamped
 * at {@link ThrustMax}. Mutated by upgrade-prize pickups, NOT by physics —
 * the live force the body feels is on the {@code RigidBody}, not here.
 *
 * @author Asser Fahrenholz
 */
public class Thrust implements EntityComponent {

    private final int thrust;

    public Thrust() {
        this(0);
    }

    public Thrust(final int thrust) {
        this.thrust = thrust;
    }

    public int getThrust() {
        return thrust;
    }

    public Thrust newAdjusted(final int delta) {
        return new Thrust(thrust + delta);
    }

    @Override
    public String toString() {
        return "Thrust[" + thrust + "]";
    }
}
