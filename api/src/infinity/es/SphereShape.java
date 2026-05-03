// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

public class SphereShape implements EntityComponent {

    private final double radius;

    protected SphereShape() {
        this(0.0);
    }

    public SphereShape(final double radius) {
        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }
}
