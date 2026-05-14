// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

/**
 * Component flagging the ship as having double-barrel weapons.
 *
 * @author Asser Fahrenholz
 */
public class DoubleBarrel {

    private final boolean enabled;

    public DoubleBarrel() {
        this(false);
    }

    public DoubleBarrel(final boolean doubleBarrel) {
        this.enabled = doubleBarrel;
    }

    public boolean isDoubleBarrel() {
        return enabled;
    }
}
