// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.jme3.math.ColorRGBA;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/**
 * Component describing a point light attached to this entity.
 *
 * @author Asser
 */
public class PointLightComponent implements EntityComponent {

    ColorRGBA color;
    float radius;
    Vec3d offset;

    public PointLightComponent(final ColorRGBA color, final float radius, final Vec3d offset) {
        this.color = color;
        this.radius = radius;
        this.offset = offset;
    }

    public PointLightComponent() {
    }

    public ColorRGBA getColor() {
        return color;
    }

    public float getRadius() {
        return radius;
    }

    public Vec3d getOffset() {
        return offset;
    }

}
