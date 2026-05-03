// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.BombLevel;

/**
 * Component holding the current mine level for this ship.
 *
 * @author Asser
 */
public class MineCurrentLevel implements EntityComponent {

    private final BombLevel level;

    public MineCurrentLevel() {
        this(null);
    }

    public MineCurrentLevel(final BombLevel level) {
        this.level = level;
    }

    public BombLevel getLevel() {
        return level;
    }

}
