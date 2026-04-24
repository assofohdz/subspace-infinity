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
 * Ceiling on {@link Thrust} — the upgrade pickup system clamps at this value:
 * {@code Thrust = min(Thrust + ThrustUpgrade, ThrustMax)}.
 *
 * <p>Maps to Subspace {@code [Ship] MaximumThrust}. Per-entity so power-ups
 * can raise the ceiling for a single ship; typically left at the template
 * value otherwise.
 *
 * @author Asser Fahrenholz
 */
public class ThrustMax implements EntityComponent {

    private final int thrustMax;

    public ThrustMax() {
        this(0);
    }

    public ThrustMax(final int thrustMax) {
        this.thrustMax = thrustMax;
    }

    public int getThrustMax() {
        return thrustMax;
    }

    public ThrustMax newAdjusted(final int delta) {
        return new ThrustMax(thrustMax + delta);
    }

    @Override
    public String toString() {
        return "ThrustMax[" + thrustMax + "]";
    }
}
