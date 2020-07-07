/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es.input;

import com.simsilica.es.EntityComponent;

/**
 * Request a frequency change. For now, there's 256 different frequencies. 
 * @author AFahrenholz
 */
public class FreqInput implements EntityComponent {

    private final byte flags;

    public FreqInput(byte flags) {
        this.flags = flags;
    }

    public byte getFlags() {
        return flags;
    }
}
