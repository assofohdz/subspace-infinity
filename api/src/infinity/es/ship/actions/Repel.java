// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Initial number of Repels given to ships when they start
 *
 * @author Asser
 */
public class Repel implements EntityComponent {

    private final int count;

    public Repel() {
        this(0);
    }

    public Repel(final int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public Repel decrement(final int decrement) {
        return new Repel(count - decrement);
    }
}
