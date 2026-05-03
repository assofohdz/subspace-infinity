// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * A buff for health. This is the component to set when we want to deduct or add health from an
 * entity.  The actual health change is calculated by the HealthSystem. This component is
 * used to communicate the desired change to the system.
 *
 * @author Paul Speed
 */
public class HealthChange implements EntityComponent {
    private final int delta;

    public HealthChange() {
        this(0);
    }

    public HealthChange(final int delta) {
        this.delta = delta;
    }

    public int getDelta() {
        return delta;
    }

    @Override
    public String toString() {
        return "HealthChange[" + delta + "]";
    }
}
