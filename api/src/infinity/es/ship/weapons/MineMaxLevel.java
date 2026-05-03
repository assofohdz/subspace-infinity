// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.BombLevel;

/**
 * Max level mines an entity can acquire
 *
 * @author Asser
 */
public class MineMaxLevel implements EntityComponent {

    private final BombLevel level;

    public MineMaxLevel() {
        this(null);
    }

    public MineMaxLevel(final BombLevel count) {
        level = count;
    }

    public BombLevel getLevel() {
        return level;
    }
}
