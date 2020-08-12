/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es;

/**
 *
 * @author Asser Fahrenholz
 */
public class TileKey {

    public static final String LEGACY = "legacy"; // Legacy SS Map Tileset
    public static final String WANGBLOB = "wangblob"; // Wang Blob Tileset

    private String tileType;
    private String tileSet;
    private short tileIndex;

    // For serialization
    public TileKey() {

    }

    public TileKey(String tileType, String tileSet, short tileIndex) {
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
