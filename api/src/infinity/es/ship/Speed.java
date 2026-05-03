// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>current effective velocity cap</b>. PlayerDriver reads this
 * each tick to clamp how fast the ship can fly — it is NOT the ship's live
 * velocity (that lives on the {@code RigidBody} and is read via
 * {@code body.getLinearVelocity()}).
 *
 * <p>Maps to Subspace {@code [Ship] InitialSpeed + n*UpgradeSpeed}, clamped
 * at {@link SpeedMax}. Mutated by upgrade-prize pickups.
 *
 * @author Asser Fahrenholz
 */
public class Speed implements EntityComponent {

    private final int speed;

    public Speed() {
        this(0);
    }

    public Speed(final int speed) {
        this.speed = speed;
    }

    public int getSpeed() {
        return speed;
    }

    public Speed newAdjusted(final int delta) {
        return new Speed(speed + delta);
    }

    @Override
    public String toString() {
        return "Speed[" + speed + "]";
    }
}
