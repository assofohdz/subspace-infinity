// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

/** Cooldown timer between gravbomb shots; ms since stamp / configured duration. */
public class GravityBombFireDelay implements FireDelay {

    private final long start;
    private final long delta;

    public GravityBombFireDelay() {
        start = System.nanoTime();
        delta = 1000000L * 10;
    }

    public GravityBombFireDelay(final long deltaMillis) {
        start = System.nanoTime();
        delta = deltaMillis * 1000000;
    }

    @Override
    public double getPercent() {
        final long time = System.nanoTime();
        return (double) (time - start) / delta;
    }

    @Override
    public String toString() {
        return "GravityBombsCooldown[" + (delta / 1000000.0) + " ms]";
    }
}
