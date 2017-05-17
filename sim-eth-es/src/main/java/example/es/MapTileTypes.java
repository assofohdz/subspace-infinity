package example.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 */
public class MapTileTypes {

    public static final String SOLID = "tile";
    public static final String PASSABLE = "passable";

    public static MapTileType solid(EntityData ed) {
        return MapTileType.create(SOLID, ed);
    }
    
    public static MapTileType create(String mapTileType, EntityData ed){
        switch(mapTileType){
            case SOLID : 
                return solid(ed);
        }
        return null;
    }
}
