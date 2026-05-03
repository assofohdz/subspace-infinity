// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Per-pickup increment (rad/sec) added to {@link Rotation} when a 'Rotation'
 * prize is collected, clamped at {@link RotationMax}.
 *
 * <p>Maps to Subspace {@code [Ship] UpgradeRotation}, converted to rad/sec.
 *
 * @author Asser Fahrenholz
 */
public class RotationUpgrade implements EntityComponent {

    private final double radSecUpgrade;

    public RotationUpgrade() {
        this(0.0);
    }

    public RotationUpgrade(final double radSecUpgrade) {
        this.radSecUpgrade = radSecUpgrade;
    }

    public double getRadSecUpgrade() {
        return radSecUpgrade;
    }

    public RotationUpgrade newAdjusted(final double delta) {
        return new RotationUpgrade(radSecUpgrade + delta);
    }

    @Override
    public String toString() {
        return "RotationUpgrade[" + radSecUpgrade + "]";
    }
}
