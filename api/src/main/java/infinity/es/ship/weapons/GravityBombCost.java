// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the energy cost to fire a gravity bomb.
 *
 * @author Asser Fahrenholz
 */
public class GravityBombCost implements EntityComponent {

    private final int cost;

    public GravityBombCost() {
        this(0);
    }

    public GravityBombCost(final int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }
}
