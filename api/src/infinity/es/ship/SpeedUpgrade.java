// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Per-pickup increment added to {@link Speed} when a 'Speed' prize is
 * collected, clamped at {@link SpeedMax}.
 *
 * <p>Maps to Subspace {@code [Ship] UpgradeSpeed}.
 *
 * @author Asser Fahrenholz
 */
public class SpeedUpgrade implements EntityComponent {

    private final int speedUpgrade;

    public SpeedUpgrade() {
        this(0);
    }

    public SpeedUpgrade(final int speedUpgrade) {
        this.speedUpgrade = speedUpgrade;
    }

    public int getSpeedUpgrade() {
        return speedUpgrade;
    }

    public SpeedUpgrade newAdjusted(final int delta) {
        return new SpeedUpgrade(speedUpgrade + delta);
    }

    @Override
    public String toString() {
        return "SpeedUpgrade[" + speedUpgrade + "]";
    }
}
