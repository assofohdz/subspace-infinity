/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import org.dyn4j.geometry.Mass;
import org.dyn4j.geometry.MassType;

/**
 *
 * @author Asser
 */
public class PhysicsMass implements EntityComponent{
    
    Mass mass;
    MassType massType;
    
    //Doesn't need empty serialization constructor because it will not be sent to client

    public PhysicsMass(Mass mass, MassType massType) {
        this.mass = mass;
        this.massType = massType;
    }
}
