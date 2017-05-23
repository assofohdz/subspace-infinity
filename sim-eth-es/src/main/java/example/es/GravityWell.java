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
public class GravityWell implements EntityComponent{
    
    double targetAreaRadius; //The uncertainty of where you pop up
    Vec3d targetLocation; //The target area for warping to
    double distance;
    double force;

    public GravityWell(double targetAreaRadius, Vec3d targetLocation, double distance, double force) {
        this.targetAreaRadius = targetAreaRadius;
        this.targetLocation = targetLocation;
        this.distance = distance;
        this.force = force;
    }

    public double getDistance() {
        return distance;
    }

    public double getForce() {
        return force;
    }
    
    public double getTargetAreaRadius() {
        return targetAreaRadius;
    }

    public Vec3d getTargetLocation() {
        return targetLocation;
    }
}
