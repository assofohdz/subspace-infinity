// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/**
 * Component on a sensor that warps any ship that touches it.
 *
 * @author Asser
 */
public class WarpTouch implements EntityComponent {

    private final double targetAreaRadius; // The uncertainty of where you pop up
    private final Vec3d targetLocation; // The target area for warping to

    public WarpTouch() {
        this(0.0, null);
    }

    public WarpTouch(final Vec3d targetLocation) {
        this(0.0, targetLocation);
    }

    public WarpTouch(final double targetAreaRadius, final Vec3d targetLocation) {
        this.targetAreaRadius = targetAreaRadius;
        this.targetLocation = targetLocation;
        if (this.targetLocation != null) {
            this.targetLocation.y = 1;
        }
    }

    public double getTargetAreaRadius() {
        return targetAreaRadius;
    }

    public Vec3d getTargetLocation() {
        return targetLocation;
    }
}
