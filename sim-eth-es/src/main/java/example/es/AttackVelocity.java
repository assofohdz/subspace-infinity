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
public class AttackVelocity implements EntityComponent{
    private double velocity;
    
    public AttackVelocity(){
        
    }
    
    public AttackVelocity(double velocity){
        this.velocity = velocity;
    }
    
    public double getVelocity(){
        return this.velocity;
    }
}
