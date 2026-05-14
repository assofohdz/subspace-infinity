// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

/**
 * Component holding the cooldown timer between bomb shots.
 *
 * @author Asser
 */
public class BombFireDelay implements FireDelay {

    private final long start;
    private final long delta;

    public BombFireDelay() {
        start = System.nanoTime();
        delta = 1000000L * 10;
    }

    public BombFireDelay(final long deltaMillis) {
        start = System.nanoTime();
        delta = deltaMillis * 1000000;
    }

    @Override
    public double getPercent() {
        final long time = System.nanoTime();
        return (double) (time - start) / delta;
    }

    /**
     * Create a new copy of this class witht the same delay
     *
     * @return new BombFireDelay instance
     */
    public BombFireDelay copy() {
        return new BombFireDelay(delta / 1000000);
    }

    @Override
    public String toString() {
        return "BombsCooldown[" + (delta / 1000000.0) + " ms]";
    }
}
