// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

/**
 * Initial number of Decoys given to ships when they start
 *
 * @author Asser
 */
public class Decoy implements InventoryCount {

    private final int count;

    public Decoy() {
        this(0);
    }

    public Decoy(final int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    @Override
    public int count() {
        return count;
    }

    public Decoy decrement(final int amount) {
        return new Decoy(count - amount);
    }
}
