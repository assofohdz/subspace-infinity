// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Maximum number of Portals allowed in ships
 *
 * @author Asser
 */
public class PortalMax implements EntityComponent {

    private final int max;

    public PortalMax() {
        this(0);
    }

    public PortalMax(final int count) {
        max = count;
    }

    public int getCount() {
        return max;
    }
}
