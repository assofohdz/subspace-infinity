// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.GunLevel;

/**
 * Initial level a ship's guns fire 0=no guns
 *
 * @author Asser
 */
public class GunCurrentLevel implements EntityComponent {

    private final GunLevel level;

    public GunCurrentLevel() {
        this(null);
    }

    public GunCurrentLevel(final GunLevel level) {
        this.level = level;
    }

    public GunLevel getLevel() {
        return level;
    }
}
