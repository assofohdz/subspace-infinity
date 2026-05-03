// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>current effective rotation-rate capability</b>, in radians
 * per second. PlayerDriver multiplies the player's rotation input by this
 * value — it is NOT the live angular velocity (that lives on the
 * {@code RigidBody} and is read via {@code body.getAngularVelocity()}).
 *
 * <p>Maps to Subspace {@code [Ship] InitialRotation + n*UpgradeRotation},
 * clamped at {@link RotationMax}. Mutated by upgrade-prize pickups. The
 * Subspace integer convention (400 units = 1 full rotation/sec) is
 * converted to rad/sec at projection time by ShipSpawnSystem.
 *
 * @author Asser Fahrenholz
 */
public class Rotation implements EntityComponent {

    private final double radSec;

    public Rotation() {
        this(0.0);
    }

    public Rotation(final double radSec) {
        this.radSec = radSec;
    }

    public double getRadSec() {
        return radSec;
    }

    public Rotation newAdjusted(final double delta) {
        return new Rotation(radSec + delta);
    }

    @Override
    public String toString() {
        return "Rotation[" + radSec + "]";
    }
}
