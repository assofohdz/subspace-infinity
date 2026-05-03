// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Ceiling on {@link Rotation} (rad/sec) — the upgrade pickup system clamps
 * at this value: {@code Rotation = min(Rotation + RotationUpgrade, RotationMax)}.
 *
 * <p>Maps to Subspace {@code [Ship] MaximumRotation}, converted to rad/sec.
 * Per-entity so power-ups can raise the ceiling for a single ship.
 *
 * @author Asser Fahrenholz
 */
public class RotationMax implements EntityComponent {

    private final double radSecMax;

    public RotationMax() {
        this(0.0);
    }

    public RotationMax(final double radSecMax) {
        this.radSecMax = radSecMax;
    }

    public double getRadSecMax() {
        return radSecMax;
    }

    public RotationMax newAdjusted(final double delta) {
        return new RotationMax(radSecMax + delta);
    }

    @Override
    public String toString() {
        return "RotationMax[" + radSecMax + "]";
    }
}
