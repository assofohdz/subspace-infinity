// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>coast-drag fraction</b>: the fraction of {@code Thrust} applied
 * as a decelerating force when the player gives no thrust intent. {@code 0} is
 * pure coast (no drag), {@code 1} decelerates as fast as full thrust
 * accelerates. Read by PlayerDriver each tick.
 *
 * <p>Not part of the canonical Subspace {@code [Ship]} INI — this is an
 * Infinity-only feel knob added because MOSS's force-based physics needs an
 * explicit drag term to stop a ship after the player releases thrust.
 * Projected at spawn from the per-arena {@code ShipConfig.dragFactor()}.
 *
 * @author Asser Fahrenholz
 */
public class DragFactor implements EntityComponent {

    private final double factor;

    public DragFactor() {
        this(0.0);
    }

    public DragFactor(final double factor) {
        this.factor = factor;
    }

    public double getFactor() {
        return factor;
    }

    @Override
    public String toString() {
        return "DragFactor[" + factor + "]";
    }
}
