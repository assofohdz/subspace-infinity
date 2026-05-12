// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Current effective rotation-rate capability (rad/sec); clamped at {@link RotationStats#max()}. */
public class Rotation implements EntityComponent {

    private final double radSec;

    public Rotation() {
        this(0.0);
    }

    public Rotation(final double radSec) {
        this.radSec = radSec;
    }

    public double getRadSec() {
        return radSec;
    }

    public Rotation newAdjusted(final double delta) {
        return new Rotation(radSec + delta);
    }

    @Override
    public String toString() {
        return "Rotation[" + radSec + "]";
    }
}
