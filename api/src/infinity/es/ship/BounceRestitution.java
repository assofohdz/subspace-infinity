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
 * The ship's <b>wall-bounce restitution</b>: the fraction of normal-velocity
 * conserved when the ship hits a static map block. {@code 1} is a perfect
 * elastic bounce (no energy loss), {@code 0.75} loses a quarter of the
 * impact energy, {@code 0} sticks. Read by ContactSystem when a ship-vs-static
 * contact is generated.
 *
 * <p>Not part of the canonical Subspace {@code [Ship]} INI — Continuum walls
 * are perfectly elastic by construction. Projected at spawn from the per-arena
 * {@code ShipConfig.bounceRestitution()}.
 *
 * @author Asser Fahrenholz
 */
public class BounceRestitution implements EntityComponent {

    private final double restitution;

    public BounceRestitution() {
        this(0.0);
    }

    public BounceRestitution(final double restitution) {
        this.restitution = restitution;
    }

    public double getRestitution() {
        return restitution;
    }

    @Override
    public String toString() {
        return "BounceRestitution[" + restitution + "]";
    }
}
