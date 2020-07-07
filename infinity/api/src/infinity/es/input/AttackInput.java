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
public class AttackInput implements EntityComponent {

    public static final byte BOMB = 0x0;
    public static final byte GUN = 0x1;
    public static final byte MINE = 0x2;
    public static final byte GRAVBOMB = 0x3;

    private byte flags;
    
    private AttackInput(){
    }
    
    public AttackInput(byte flags) {
        this.flags = flags;
    }

    public byte getFlags() {
        return flags;
    }

}
