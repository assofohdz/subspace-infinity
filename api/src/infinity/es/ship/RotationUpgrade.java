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
 * Per-pickup increment (rad/sec) added to {@link Rotation} when a 'Rotation'
 * prize is collected, clamped at {@link RotationMax}.
 *
 * <p>Maps to Subspace {@code [Ship] UpgradeRotation}, converted to rad/sec.
 *
 * @author Asser Fahrenholz
 */
public class RotationUpgrade implements EntityComponent {

    private final double radSecUpgrade;

    public RotationUpgrade() {
        this(0.0);
    }

    public RotationUpgrade(final double radSecUpgrade) {
        this.radSecUpgrade = radSecUpgrade;
    }

    public double getRadSecUpgrade() {
        return radSecUpgrade;
    }

    public RotationUpgrade newAdjusted(final double delta) {
        return new RotationUpgrade(radSecUpgrade + delta);
    }

    @Override
    public String toString() {
        return "RotationUpgrade[" + radSecUpgrade + "]";
    }
}
