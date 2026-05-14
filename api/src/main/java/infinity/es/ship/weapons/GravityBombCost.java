// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

/**
 * Component holding the energy cost to fire a gravity bomb. Drift outlier — the canonical
 * shape (BulletStats/BombStats/MineStats) carries cost on the stats record; GravBomb is missing
 * a {@code GravBombStats} record. Tracked in {@code .scratch/code-todos-backlog.md}.
 *
 * @author Asser Fahrenholz
 */
public class GravityBombCost implements EnergyCost {

    private final int cost;

    public GravityBombCost() {
        this(0);
    }

    public GravityBombCost(final int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }

    @Override
    public int energyCost() {
        return cost;
    }
}
