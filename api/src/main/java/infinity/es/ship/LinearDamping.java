// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Per-ship linear-damping coefficient passed to mphys's {@code RigidBody.setDamping}; {@code 1.0} = no damping. Infinity extension — not in Subspace canon. */
public class LinearDamping implements EntityComponent {

    private final double damping;

    public LinearDamping() {
        this(1.0);
    }

    public LinearDamping(final double damping) {
        this.damping = damping;
    }

    public double getDamping() {
        return damping;
    }

    @Override
    public String toString() {
        return "LinearDamping[" + damping + "]";
    }
}
