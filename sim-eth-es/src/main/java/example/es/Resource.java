/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;

/**
 *
 * @author ss
 */
public class Resource implements EntityComponent{
 
    // in the order:
    // 0: Gold
    // 1: 
    private int[] resources;
    
    
    public Resource() {
    }

    public Resource(int[] resources) {
        this.resources = resources;
                
    }

    public int[] getResources() {
        return this.resources;
    }
    
}
