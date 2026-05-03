// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>live energy pool</b>: the value that depletes when the ship
 * fires weapons or takes projectile damage, and refills via {@link Recharge}
 * up to the current effective cap {@link Energy}. Reaches zero ⇒ ship dies.
 *
 * <p>Distinct from {@link Energy} (the upgradeable cap that this pool tops
 * out at) and {@link EnergyMax} (the absolute hard cap on the cap itself).
 * The pool is seeded at spawn from {@code ShipConfig.energy().initial()} by
 * {@code ShipSpawnSystem}, mutated each tick by {@code EnergySystem}, and
 * refilled to the current cap by the QUICKCHARGE prize.
 *
 * @author Asser Fahrenholz
 */
public class Health implements EntityComponent {

    private final int health;

    public Health() {
        this(0);
    }

    public Health(final int health) {
        this.health = health;
    }

    public int getHealth() {
        return health;
    }

    public Health newAdjusted(final int delta) {
        return new Health(health + delta);
    }

    @Override
    public String toString() {
        return "Health[" + health + "]";
    }
}
