// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

/**
 * Current number of Thor's Hammers in a ship
 *
 * @author Asser
 */
public class ThorCurrentCount implements InventoryCount {

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

    @Override
    public int count() {
        return count;
    }

    public ThorCurrentCount add(final int delta) {
        return new ThorCurrentCount(this.count + delta);
    }

    public ThorCurrentCount subtract(final int delta) {
        return new ThorCurrentCount(this.count - delta);
    }
}
