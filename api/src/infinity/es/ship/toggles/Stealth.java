// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/**
 * Indicates if a ship has stealth enabled
 *
 * @author Asser
 */
public class Stealth implements EntityComponent {

    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public Stealth(final boolean enabled) {
        this.enabled = enabled;
    }

    public Stealth() {
    }
}
