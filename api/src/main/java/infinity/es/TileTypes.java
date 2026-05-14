// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class TileTypes {

    private TileTypes() { /* utility class */ }

    public static final String LEGACY = "legacy"; // Legacy SS Map Tileset
    public static final String WANGBLOB = "wangblob"; // Wang Blob Tileset

    public static TileType legacy(final String tileSet, final short tileIndex, final EntityData ed) {
        return TileType.create(LEGACY, tileSet, tileIndex, ed);
    }

    public static TileType wangblob(final String tileSet, final short tileIndex, final EntityData ed) {
        return TileType.create(WANGBLOB, tileSet, tileIndex, ed);
    }
}
