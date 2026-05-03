// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Maximum number of Decoys allowed in ships
 *
 * @author Asser
 */
public class DecoyMax implements EntityComponent {

    private final int max;

    public DecoyMax() {
        this(0);
    }

    public DecoyMax(final int count) {
        max = count;
    }

    public int getCount() {
        return max;
    }
}
