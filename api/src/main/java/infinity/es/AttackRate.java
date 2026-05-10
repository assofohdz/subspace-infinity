// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the time between attacks (milliseconds).
 *
 * @author ss
 */
public class AttackRate implements EntityComponent {
    private final int rate; // [ms]

    public AttackRate() {
        this(0);
    }

    public AttackRate(final int rate) {
        this.rate = rate;
    }

    public int getRate() {
        return rate;
    }

}
