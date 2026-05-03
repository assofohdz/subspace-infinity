// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the cooldown timer between gun shots.
 *
 * @author Asser
 */
public class GunFireDelay implements EntityComponent {

    private final long start;
    private final long delta;

    public GunFireDelay() {
        start = System.nanoTime();
        delta = 1000000 * 10;
    }

    public GunFireDelay(final long deltaMillis) {
        start = System.nanoTime();
        delta = deltaMillis * 1000000;
    }

    public double getPercent() {
        final long time = System.nanoTime();
        return (double) (time - start) / delta;
    }

    /**
     * Create a new copy of this class witht the same delay
     *
     * @return new BombFireDelay instance
     */
    public GunFireDelay copy() {
        return new GunFireDelay(delta / 1000000);
    }

    @Override
    public String toString() {
        return "GunsCooldown[" + (delta / 1000000.0) + " ms]";
    }
}
