// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Initial number of Portals given to ships when they start
 *
 * @author Asser
 */
public class Portal implements EntityComponent {

    private final int count;

    public Portal() {
        this(0);
    }

    public Portal(final int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }
}
