// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Ceiling on {@link Speed} — the upgrade pickup system clamps at this value:
 * {@code Speed = min(Speed + SpeedUpgrade, SpeedMax)}.
 *
 * <p>Maps to Subspace {@code [Ship] MaximumSpeed}. Per-entity so power-ups
 * can raise the ceiling for a single ship; typically left at the template
 * value otherwise.
 *
 * @author Asser Fahrenholz
 */
public class SpeedMax implements EntityComponent {

    private final int speedMax;

    public SpeedMax() {
        this(0);
    }

    public SpeedMax(final int speedMax) {
        this.speedMax = speedMax;
    }

    public int getSpeedMax() {
        return speedMax;
    }

    public SpeedMax newAdjusted(final int delta) {
        return new SpeedMax(speedMax + delta);
    }

    @Override
    public String toString() {
        return "SpeedMax[" + speedMax + "]";
    }
}
