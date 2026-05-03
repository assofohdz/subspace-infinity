// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/**
 * Amount of energy required to have 'Anti-Warp' activated (thousanths per
 * hundredth of a second)
 *
 * @author Asser
 */
public class AntiwarpEnergy implements EntityComponent {

    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public AntiwarpEnergy(final boolean enabled) {
        this.enabled = enabled;
    }

    public AntiwarpEnergy() {
    }
}
