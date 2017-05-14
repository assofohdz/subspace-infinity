package example.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 */
public class MapTileTypes {

    public static final String TILE = "tile";

    public static MapTileType tile(EntityData ed) {
        return MapTileType.create(TILE, ed);
    }
    
    public static MapTileType create(String mapTileType, EntityData ed){
        switch(mapTileType){
            case TILE : 
                return tile(ed);
        }
        return null;
    }
}
