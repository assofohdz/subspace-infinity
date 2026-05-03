// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>turn-responsiveness rate constant</b> (1/sec): how fast the
 * angular velocity approaches the target rotation rate. Higher = snappier
 * turn response, lower = more sluggish/heavy ship feel. Used as the rate
 * constant in PlayerDriver's frame-rate-independent exponential approach;
 * {@code 8.0} reaches ~95% of target in ~0.4 seconds.
 *
 * <p>Not part of the canonical Subspace {@code [Ship]} INI — Continuum's
 * client-authoritative integer rotation has no equivalent. Projected at spawn
 * from the per-arena {@code ShipConfig.turnResponsiveness()}.
 *
 * @author Asser Fahrenholz
 */
public class TurnResponsiveness implements EntityComponent {

    private final double rate;

    public TurnResponsiveness() {
        this(0.0);
    }

    public TurnResponsiveness(final double rate) {
        this.rate = rate;
    }

    public double getRate() {
        return rate;
    }

    @Override
    public String toString() {
        return "TurnResponsiveness[" + rate + "]";
    }
}
