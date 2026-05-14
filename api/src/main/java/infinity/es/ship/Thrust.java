// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Current effective thrust rate (acceleration units/sec); clamped at {@link ThrustStats#max()}. */
public class Thrust implements EntityComponent {

    private final int value;

    public Thrust() {
        this(0);
    }

    public Thrust(final int thrust) {
        this.value = thrust;
    }

    public int getThrust() {
        return value;
    }

    public Thrust newAdjusted(final int delta) {
        return new Thrust(value + delta);
    }

    @Override
    public String toString() {
        return "Thrust[" + value + "]";
    }
}
