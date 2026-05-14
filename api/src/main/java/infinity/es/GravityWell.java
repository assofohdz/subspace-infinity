// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component describing a gravitational pull or push field.
 *
 * @author Asser
 */
public class GravityWell implements EntityComponent {

    public static final String PULL = "pull";
    public static final String PUSH = "push";

    private final double distance;
    private final double force;
    private final String gravityType;

    public GravityWell() {
        this(0.0, 0.0, null);
    }

    public GravityWell(final double distance, final double force, final String gravityType) {
        this.distance = distance;
        this.force = Math.abs(force);
        this.gravityType = gravityType;
    }

    public double getDistance() {
        return distance;
    }

    public double getForce() {
        return force;
    }

    public String getGravityType() {
        return gravityType;
    }

}
