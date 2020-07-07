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
public class AvatarInput implements EntityComponent {
    
    public static final byte SPEC = 0x0;
    public static final byte WARBIRD = 0x1;
    public static final byte JAVELIN = 0x2;
    public static final byte SPIDER = 0x3;
    public static final byte LEVI = 0x4;
    public static final byte TERRIER = 0x5;
    public static final byte LANCASTER = 0x6;
    public static final byte WEASEL = 0x7;
    public static final byte SHARK = 0x8;
    
    private byte flags;
    
    private AvatarInput(){
    }

    public AvatarInput(byte flags) {
        this.flags = flags;
    }

    public byte getFlags() {
        return flags;
    }
    
}
