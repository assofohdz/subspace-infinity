package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;


/**
 *  For attacking game objects, this is the type of attack
 *
 *  @author    Paul Speed
 */
public class ToggleType implements EntityComponent {
    
    private int type;
 
    protected ToggleType() {
    }
    
    public ToggleType( int type ) {
        this.type = type;
    }
    
    public static ToggleType create( String typeName, EntityData ed ) {
        return new ToggleType(ed.getStrings().getStringId(typeName, true));
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
