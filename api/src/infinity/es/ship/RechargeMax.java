// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
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
