// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Maximum number of Repels allowed in ships
 *
 * @author Asser
 */
public class RepelMax implements EntityComponent {

    private final int max;

    public RepelMax() {
        this(0);
    }

    public RepelMax(final int count) {
        max = count;
    }

    public int getCount() {
        return max;
    }
}
