// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/**
 * Component requesting a warp to a target world location.
 *
 * @author Asser
 */
public class WarpTo implements EntityComponent {

    private final Vec3d targetLocation;

    public WarpTo() {
        this(new Vec3d());
    }

    public WarpTo(final Vec3d targetLocation) {
        this.targetLocation = targetLocation;
    }

    public Vec3d getTargetLocation() {
        return targetLocation;
    }
}
