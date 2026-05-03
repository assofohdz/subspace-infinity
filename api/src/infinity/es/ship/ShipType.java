// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;
import infinity.Ship;

/**
 * Marks a ship entity's type (Warbird, Javelin, …). Used by the spawn system
 * to look up the matching {@code ShipConfig} template in the per-arena
 * {@code ConfigRegistry} and project its values onto the entity's stat
 * components.
 *
 * @author Asser Fahrenholz
 */
public class ShipType implements EntityComponent {

    private final Ship type;

    public ShipType() {
        this(null);
    }

    public ShipType(final Ship type) {
        this.type = type;
    }

    public Ship getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ShipType[" + type + "]";
    }
}
