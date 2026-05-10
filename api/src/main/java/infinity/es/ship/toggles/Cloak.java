// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/**
 * Indicates if a ship has cloak enabled
 *
 * @author Asser
 */
public class Cloak implements EntityComponent {

    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public Cloak(final boolean enabled) {
        this.enabled = enabled;
    }

    public Cloak() {
    }
}
