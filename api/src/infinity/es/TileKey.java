// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

/**
 * Lookup key identifying a tile by tileset, type, and index.
 *
 * @author Asser Fahrenholz
 */
public class TileKey {

    public static final String LEGACY = "legacy"; // Legacy SS Map Tileset
    public static final String WANGBLOB = "wangblob"; // Wang Blob Tileset

    private final String tileType;
    private final String tileSet;
    private final short tileIndex;

    // For serialization
    public TileKey() {
        this(null, null, (short) 0);
    }

    public TileKey(final String tileType, final String tileSet, final short tileIndex) {
        this.tileType = tileType;
        this.tileSet = tileSet;
        this.tileIndex = tileIndex;
    }

    public String getTileType() {
        return tileType;
    }

    public String getTileSet() {
        return tileSet;
    }

    public short getTileIndex() {
        return tileIndex;
    }

    @Override
    public String toString() {
        return "TileKey{" + "tileType=" + tileType + ", tileSet=" + tileSet + ", tileIndex=" + tileIndex + '}';
    }

}
