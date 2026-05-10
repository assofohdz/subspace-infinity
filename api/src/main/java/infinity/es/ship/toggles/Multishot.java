// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.toggles;

import com.simsilica.es.EntityComponent;

/**
 * Toggle component enabling multi-barrel firing.
 *
 * @author Asser
 */
public class Multishot implements EntityComponent {

    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public Multishot(final boolean enabled) {
        this.enabled = enabled;
    }

    public Multishot() {
    }
}
