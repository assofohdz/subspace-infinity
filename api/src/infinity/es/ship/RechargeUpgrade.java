// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Per-pickup increment (energy/sec) added to {@link Recharge} when a
 * 'Recharge Rate' prize is collected, clamped at {@link RechargeMax}.
 *
 * <p>Maps to Subspace {@code [Ship] UpgradeRecharge}, converted to per-sec.
 *
 * @author Asser Fahrenholz
 */
public class RechargeUpgrade implements EntityComponent {

    private final double rechargePerSecondUpgrade;

    public RechargeUpgrade() {
        this(0.0);
    }

    public RechargeUpgrade(final double rechargePerSecondUpgrade) {
        this.rechargePerSecondUpgrade = rechargePerSecondUpgrade;
    }

    public double getRechargePerSecondUpgrade() {
        return rechargePerSecondUpgrade;
    }

    public RechargeUpgrade newAdjusted(final double delta) {
        return new RechargeUpgrade(rechargePerSecondUpgrade + delta);
    }

    @Override
    public String toString() {
        return "RechargeUpgrade[" + rechargePerSecondUpgrade + "]";
    }
}
