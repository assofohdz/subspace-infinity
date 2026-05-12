// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Current effective velocity cap; clamped at {@link SpeedStats#max()}. */
public class Speed implements EntityComponent {

    private final int speed;

    public Speed() {
        this(0);
    }

    public Speed(final int speed) {
        this.speed = speed;
    }

    public int getSpeed() {
        return speed;
    }

    public Speed newAdjusted(final int delta) {
        return new Speed(speed + delta);
    }

    @Override
    public String toString() {
        return "Speed[" + speed + "]";
    }
}
