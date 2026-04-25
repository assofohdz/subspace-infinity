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
 * The ship's <b>current effective energy cap</b>: the value the live
 * {@link Health} pool tops out at when recharging, and the upgradeable axis
 * the ENERGY prize bumps via {@link EnergyUpgrade}, hard-clamped at
 * {@link EnergyMax}. Same role as {@link Thrust}/{@link Speed}/etc. for the
 * energy pool.
 *
 * <p>Maps to Subspace's running {@code MaximumEnergy} (which grows from
 * {@code InitialEnergy} toward {@code MaximumEnergy} via energy prizes).
 * Mutated by upgrade-prize pickups, NOT by damage — damage and recharge
 * mutate {@link Health}, not this.
 *
 * @author Asser Fahrenholz
 */
public class Energy implements EntityComponent {

    private final int energy;

    public Energy() {
        this(0);
    }

    public Energy(final int energy) {
        this.energy = energy;
    }

    public int getEnergy() {
        return energy;
    }

    public Energy newAdjusted(final int delta) {
        return new Energy(energy + delta);
    }

    @Override
    public String toString() {
        return "Energy[" + energy + "]";
    }
}
