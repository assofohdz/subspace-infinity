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
public class ToggleInput implements EntityComponent{
    
    public static final byte ANTIWARP_ENABLE = 0x0;
    public static final byte ANTIWARP_DISABLE = 0x1;
    public static final byte CLOAK_ENABLE = 0x2;
    public static final byte CLOAK_DISABLE = 0x3;
    public static final byte STEALTH_ENABLE = 0x4;
    public static final byte STEALTH_DISABLE = 0x5;
    public static final byte XRADAR_ENABLE = 0x6;
    public static final byte XRADAR_DISABLE = 0x7;
    
    private byte flags;
    
    private ToggleInput(){
    }

    public byte getFlags() {
        return flags;
    }

    public ToggleInput(byte flags) {
        this.flags = flags;
    }
    
    
}
