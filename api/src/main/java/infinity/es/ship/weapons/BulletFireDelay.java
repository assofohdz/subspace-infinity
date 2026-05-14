// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

/**
 * Component holding the cooldown timer between bullet shots.
 *
 * @author Asser
 */
public class BulletFireDelay implements FireDelay {

    private final long start;
    private final long delta;

    public BulletFireDelay() {
        start = System.nanoTime();
        delta = 1000000L * 10;
    }

    public BulletFireDelay(final long deltaMillis) {
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
    public BulletFireDelay copy() {
        return new BulletFireDelay(delta / 1000000);
    }

    @Override
    public String toString() {
        return "GunsCooldown[" + (delta / 1000000.0) + " ms]";
    }
}
