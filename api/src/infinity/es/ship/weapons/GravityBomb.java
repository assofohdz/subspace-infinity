// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.BombLevel;

/**
 * Component holding the gravity-bomb level for this projectile.
 *
 * @author Asser
 */
public class GravityBomb implements EntityComponent {

    private final BombLevel level;

    public GravityBomb() {
        this(null);
    }

    public GravityBomb(final BombLevel level) {
        this.level = level;
    }

    public BombLevel getLevel() {
        return level;
    }
}
