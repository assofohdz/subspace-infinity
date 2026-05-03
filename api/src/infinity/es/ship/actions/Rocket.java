// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Initial number of Rockets given to ships when they start
 *
 * @author Asser
 */
public class Rocket implements EntityComponent {

    private final int count;

    public Rocket() {
        this(0);
    }

    public Rocket(final int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public Rocket decrement(final int amount) {
        return new Rocket(count - amount);
    }
}
