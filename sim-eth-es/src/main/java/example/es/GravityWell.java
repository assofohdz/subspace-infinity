/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/**
 *
 * @author Asser
 */
public class GravityWell implements EntityComponent {

    double distance;
    double force;

    public GravityWell(double distance, double force) {
        this.distance = distance;
        this.force = force;
    }

    public double getDistance() {
        return distance;
    }

    public double getForce() {
        return force;
    }

}
