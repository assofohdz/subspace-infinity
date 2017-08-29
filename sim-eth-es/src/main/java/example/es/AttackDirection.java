/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
import example.sim.SimplePhysics;
import java.util.LinkedList;
import org.dyn4j.dynamics.DetectResult;
import org.dyn4j.geometry.Vector2;

/**
 *
 * @author ss
 */
public class AttackDirection implements EntityComponent{
    private  Vector2 direction;
        
    public AttackDirection(){
        
    }
    
    public AttackDirection(Vector2 direction){
        this.direction = direction;
    }
    
    public Vector2 getMethod(){
        return this.direction;
    }
    
}
