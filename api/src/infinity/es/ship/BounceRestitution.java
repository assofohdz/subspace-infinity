// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>wall-bounce restitution</b>: the fraction of normal-velocity
 * conserved when the ship hits a static map block. {@code 1} is a perfect
 * elastic bounce (no energy loss), {@code 0.75} loses a quarter of the
 * impact energy, {@code 0} sticks. Read by ContactSystem when a ship-vs-static
 * contact is generated.
 *
 * <p>Not part of the canonical Subspace {@code [Ship]} INI — Continuum walls
 * are perfectly elastic by construction. Projected at spawn from the per-arena
 * {@code ShipConfig.bounceRestitution()}.
 *
 * @author Asser Fahrenholz
 */
public class BounceRestitution implements EntityComponent {

    private final double restitution;

    public BounceRestitution() {
        this(0.0);
    }

    public BounceRestitution(final double restitution) {
        this.restitution = restitution;
    }

    public double getRestitution() {
        return restitution;
    }

    @Override
    public String toString() {
        return "BounceRestitution[" + restitution + "]";
    }
}
