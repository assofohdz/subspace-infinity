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
public class Warp implements EntityComponent {

    double targetAreaRadius; //The uncertainty of where you pop up
    Vec3d targetLocation; //The target area for warping to{

    public Warp(double targetAreaRadius, Vec3d targetLocation) {
        this.targetAreaRadius = targetAreaRadius;
        this.targetLocation = targetLocation;
    }

    public double getTargetAreaRadius() {
        return targetAreaRadius;
    }

    public Vec3d getTargetLocation() {
        return targetLocation;
    }
}
