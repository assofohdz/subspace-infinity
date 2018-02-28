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
public class AttackRate implements EntityComponent{
    private int rate; // [ms]
    
    public AttackRate(){
        
    }
    
    public AttackRate(int rate){
        this.rate = rate;
    }
    
    public int getRate(){
        return this.rate;
    }
    
}
