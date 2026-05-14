// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the cooldown timer between mine drops.
 *
 * @author Asser
 */
public class MineFireDelay implements EntityComponent {

    private final long start;
    private final long delta;

    public MineFireDelay() {
        start = System.nanoTime();
        delta = 1000000L * 10;
    }

    public MineFireDelay(final long deltaMillis) {
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
    public MineFireDelay copy() {
        return new MineFireDelay(delta / 1000000);
    }

    @Override
    public String toString() {
        return "MinesCooldown[" + (delta / 1000000.0) + " ms]";
    }
}
