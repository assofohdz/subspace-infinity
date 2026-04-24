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
 * Ceiling on {@link Recharge} (energy/sec) — the upgrade pickup system clamps
 * at this value: {@code Recharge = min(Recharge + RechargeUpgrade, RechargeMax)}.
 *
 * <p>Maps to Subspace {@code [Ship] MaximumRecharge}, converted to per-sec.
 * Per-entity so power-ups can raise the ceiling for a single ship.
 *
 * @author Asser
 */
public class RechargeMax implements EntityComponent {

    private final double maxRechargePerSecond;

    public RechargeMax() {
        this(0.0);
    }

    public RechargeMax(final double maxRechargePerSecond) {
        this.maxRechargePerSecond = maxRechargePerSecond;
    }

    public double getMaxRechargePerSecond() {
        return maxRechargePerSecond;
    }

    public RechargeMax newAdjusted(final double delta) {
        return new RechargeMax(maxRechargePerSecond + delta);
    }

    @Override
    public String toString() {
        return "RechargeMax[" + maxRechargePerSecond + "]";
    }
}
