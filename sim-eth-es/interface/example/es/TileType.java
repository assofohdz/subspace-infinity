package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/**
 * Indicates the type of tile. Could be Legacy or Wang Blob
 *
 * @author Paul Speed
 */
public class TileType implements EntityComponent {

    private int type;
    private String tileSet;
    private short tileIndex;

    public TileType(int type, String tileSet, short tileIndex) {
        this.type = type;
        this.tileSet = tileSet;
        this.tileIndex = tileIndex;
    }

    protected TileType() {
    }

    public static TileType create(String typeName, String tileSet, short tileIndex, EntityData ed) {
        return new TileType(ed.getStrings().getStringId(typeName, true), tileSet, tileIndex);
    }

    public int getType() {
        return type;
    }

    public String getTypeName(EntityData ed) {
        return ed.getStrings().getString(type);
    }
    
    public TileType newTileIndex(short newTileIndex, EntityData ed){
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
