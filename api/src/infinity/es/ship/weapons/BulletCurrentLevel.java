// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.BulletLevel;

/**
 * Initial level a ship's bullets fire 0=no bullets
 *
 * @author Asser
 */
public class BulletCurrentLevel implements EntityComponent {

    private final BulletLevel level;

    public BulletCurrentLevel() {
        this(null);
    }

    public BulletCurrentLevel(final BulletLevel level) {
        this.level = level;
    }

    public BulletLevel getLevel() {
        return level;
    }
}
