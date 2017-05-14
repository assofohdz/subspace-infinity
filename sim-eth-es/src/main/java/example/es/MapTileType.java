package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;


/**
 *  For map tile types
 *
 */
public class MapTileType implements EntityComponent {
    
    private int type;
 
    protected MapTileType() {
    }
    
    public MapTileType( int type ) {
        this.type = type;
    }
    
    public static MapTileType create( String typeName, EntityData ed ) {
        return new MapTileType(ed.getStrings().getStringId(typeName, true));
    }
    
    public int getType() {
        return type;
    }
    
    public String getTypeName( EntityData ed ) {
        return ed.getStrings().getString(type);                 
    }
 
    @Override   
    public String toString() {
        return getClass().getSimpleName() + "[type=" + type + "]";
    }     
}
