// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Current number of Thor's Hammers in a ship
 *
 * @author Asser
 */
public class ThorCurrentCount implements EntityComponent {

    private final int count;

    public ThorCurrentCount() {
        this(0);
    }

    public ThorCurrentCount(final int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public ThorCurrentCount add(final int count) {
        return new ThorCurrentCount(this.count + count);
    }

    public ThorCurrentCount subtract(final int count) {
        return new ThorCurrentCount(this.count - count);
    }
}
