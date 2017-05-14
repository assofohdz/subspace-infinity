/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import org.dyn4j.collision.Fixture;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Triangle;

/**
 *
 * @author Asser
 */
public class PhysicsShape implements EntityComponent{
    
    BodyFixture fixture;
    
    //Doesn't need empty serialization constructor because it will not be sent to client
    
    public PhysicsShape(BodyFixture fixture) {
        this.fixture = fixture;
    }

    public BodyFixture getFixture() {
        return fixture;
    }
}
