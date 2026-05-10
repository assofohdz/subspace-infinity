// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Maximum number of Rockets allowed in ships
 *
 * @author Asser
 */
public class RocketMax implements EntityComponent {

    private final int max;

    public RocketMax() {
        this(0);
    }

    public RocketMax(final int count) {
        max = count;
    }

    public int getCount() {
        return max;
    }
}
