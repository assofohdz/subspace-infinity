// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the projectile launch velocity for this entity's attacks.
 *
 * @author ss
 */
public class AttackVelocity implements EntityComponent {
    private final double velocity;

    public AttackVelocity() {
        this(0.0);
    }

    public AttackVelocity(final double velocity) {
        this.velocity = velocity;
    }

    public double getVelocity() {
        return velocity;
    }
}
