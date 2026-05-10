// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the attack range of this entity.
 *
 * @author ss
 */
public class AttackRange implements EntityComponent {

    private final double range;

    public AttackRange() {
        this(0.0);
    }

    public AttackRange(final double range) {
        this.range = range;
    }

    public double getRange() {
        return range;
    }

}
