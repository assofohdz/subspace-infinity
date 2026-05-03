// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The world-unit radius around the ship that its radar viewport displays.
 * Read by the client {@code RadarState} to size the off-screen camera frustum
 * and the radar leaf-paging radius.
 *
 * <p>Not part of the canonical Subspace {@code [Ship]} INI — Continuum's radar
 * draws the whole arena. This is an Infinity-only knob added so each ship
 * class can have its own radar reach. Projected at spawn from the per-arena
 * {@code ShipConfig.radarRange()}.
 *
 * @author Asser Fahrenholz
 */
public class RadarRange implements EntityComponent {

    private final double range;

    public RadarRange() {
        this(0.0);
    }

    public RadarRange(final double range) {
        this.range = range;
    }

    public double getRange() {
        return range;
    }

    @Override
    public String toString() {
        return "RadarRange[" + range + "]";
    }
}
