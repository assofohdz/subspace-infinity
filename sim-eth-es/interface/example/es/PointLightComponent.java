/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.jme3.math.ColorRGBA;
import com.simsilica.es.EntityComponent;

/**
 *
 * @author Asser
 */
public class PointLightComponent implements EntityComponent {

    ColorRGBA color;
    float radius;

    public PointLightComponent(ColorRGBA color, float radius) {
        this.color = color;
        this.radius = radius;
    }

    public PointLightComponent() {
    }

    public ColorRGBA getColor() {
        return color;
    }

    public float getRadius() {
        return radius;
    }

}
