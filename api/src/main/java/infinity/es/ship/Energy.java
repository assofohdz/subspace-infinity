// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * The ship's <b>live energy pool</b>: the value that depletes when the ship
 * fires weapons or takes projectile damage, and refills via the recharge
 * rate stored in {@link EnergyStats} up to {@code EnergyStats.max}.
 * Reaches zero ⇒ ship dies.
 *
 * <p>Was named {@code Health} pre-ADR-0001; renamed in the Energy aspect
 * pilot so the directory shape is the canonical {@code Energy} +
 * {@link EnergyStats} + {@code EnergyChange} +
 * {@code EnergyStatsChange} quadruple every ship aspect mirrors. The
 * Subspace canon was always "energy pool"; the prior {@code Health}
 * name was carryover from the engine's generic damageable-entity HUD.
 *
 * <p><b>Canonical writer:</b> {@code EnergySystem} (drains
 * {@code EnergyChange} + {@link infinity.es.ChangeTarget} entities,
 * folds same-tick deltas, applies regen, triggers death at zero).
 * {@code ShipSpawnSystem} is the spawn-time projector (factory tier,
 * exempt from RaM).
 *
 * <p>Distinct from {@link EnergyStats#max()} (the upgradeable cap that
 * this pool tops out at) and {@link EnergyStats#hardMax()} (the
 * absolute hard cap on that cap). Seeded at spawn from
 * {@code ShipConfig.energy().initial()} by {@code ShipSpawnSystem},
 * mutated each tick by {@code EnergySystem}, and refilled to the
 * current cap by the QUICKCHARGE prize.
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
