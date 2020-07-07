/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es.input;

import com.simsilica.es.EntityComponent;

/**
 *
 * @author AFahrenholz
 */
public class MapInput implements EntityComponent{
    
    public static final byte CREATE = 0x0;
    public static final byte READ = 0x1;
    public static final byte UPDATE = 0x2;
    public static final byte DELETE = 0x3;
    
    private byte flags;
    
    private MapInput(){
    }

    public byte getFlags() {
        return flags;
    }

    public MapInput(byte flags) {
        this.flags = flags;
    }
}
