// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.BombLevel;

/**
 * Max level bombs an entity can acquire
 *
 * @author Asser
 */
public class BombMaxLevel implements EntityComponent {

    private final BombLevel level;

    public BombMaxLevel() {
        this(null);
    }

    public BombMaxLevel(final BombLevel level) {
        this.level = level;
    }

    public BombLevel getLevel() {
        return level;
    }
}
