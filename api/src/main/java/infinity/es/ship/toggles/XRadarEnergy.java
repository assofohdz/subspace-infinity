// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/**
 * Amount of energy required to have 'X-Radar' activated (thousanths per
 * hundredth of a second)
 *
 * @author Asser
 */
public class XRadarEnergy implements EntityComponent {

    private final int energyDrain;

    public XRadarEnergy() {
        this(0);
    }

    public XRadarEnergy(final int energyDrain) {
        this.energyDrain = energyDrain;
    }

    public int getEnergy() {
        return energyDrain;
    }
}
