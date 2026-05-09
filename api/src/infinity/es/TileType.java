// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/**
 * Indicates the type of tile. Could be Legacy or Wang Blob
 *
 * @author Paul Speed
 */
public class TileType implements EntityComponent {

    private final int type;
    private final String tileSet;
    private final short tileIndex;

    public TileType(final int type, final String tileSet, final short tileIndex) {
        this.type = type;
        this.tileSet = tileSet;
        this.tileIndex = tileIndex;
    }

    protected TileType() {
        this(0, null, (short) 0);
    }

    public static TileType create(final String typeName, final String tileSet, final short tileIndex,
            final EntityData ed) {
        return new TileType(ed.getStrings().getStringId(typeName, true), tileSet, tileIndex);
    }

    public int getType() {
        return type;
    }

    public String getTypeName(final EntityData ed) {
        return ed.getStrings().getString(type);
    }

    public TileType newTileIndex(final short newTileIndex) {
        return new TileType(type, tileSet, newTileIndex);
    }

    public String getTileSet() {
        return tileSet;
    }

    public short getTileIndex() {
        return tileIndex;
    }

    @Override
    public String toString() {
        return "TileType{" + "type=" + type + ", tileSet=" + tileSet + ", tileIndex=" + tileIndex + '}';
    }
}
