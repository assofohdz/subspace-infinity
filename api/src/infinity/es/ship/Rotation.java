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
