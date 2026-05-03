// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/**
 * Amount of energy required to have 'Stealth' activated (thousanths per
 * hundredth of a second)
 *
 * @author Asser
 */
public class StealthEnergy implements EntityComponent {

    private final int energyDrain;

    public StealthEnergy() {
        this(0);
    }

    public StealthEnergy(final int energyDrain) {
        this.energyDrain = energyDrain;
    }

    public int getEnergy() {
        return energyDrain;
    }
}
