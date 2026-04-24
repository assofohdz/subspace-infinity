/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
