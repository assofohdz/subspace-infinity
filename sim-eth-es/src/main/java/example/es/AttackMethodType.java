/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/**
 *
 * @author ss
 */
public class AttackMethodType implements EntityComponent{
    
    private int type;
 
    protected AttackMethodType() {
    }
    
    public AttackMethodType( int type ) {
        this.type = type;
    }
    
    public static AttackMethodType create( String typeName, EntityData ed ) {
        return new AttackMethodType(ed.getStrings().getStringId(typeName, true));
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
