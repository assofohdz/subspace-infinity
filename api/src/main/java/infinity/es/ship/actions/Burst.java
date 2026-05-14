// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

/**
 * Initial number of Bursts given to ships when they start
 *
 * @author Asser
 */
public class Burst implements InventoryCount {

    private final int count;

    public Burst() {
        this(0);
    }

    public Burst(final int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    @Override
    public int count() {
        return count;
    }
}
