// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.BombLevel;

/**
 * Initial level a ship's bombs fire 0=no bombs
 *
 * @author Asser
 */
public class BombCurrentLevel implements EntityComponent {

    private final BombLevel level;

    public BombCurrentLevel() {
        this(null);
    }

    public BombCurrentLevel(final BombLevel level) {
        this.level = level;
    }

    public BombLevel getLevel() {
        return level;
    }
}
