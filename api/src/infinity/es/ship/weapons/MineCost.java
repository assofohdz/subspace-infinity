// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the energy cost to drop a mine.
 *
 * @author Asser Fahrenholz
 */
public class MineCost implements EntityComponent {

    private final int cost;

    public MineCost() {
        this(0);
    }

    public MineCost(final int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }
}
