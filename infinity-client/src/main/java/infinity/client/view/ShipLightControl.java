// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;

import com.jme3.light.PointLight;
import com.jme3.math.Vector3f;
import com.jme3.scene.control.LightControl;

/**
 * Attaches a point light to a spatial; positions the light slightly above
 * the spatial's world translation each frame.
 *
 * @author AFahrenholz
 */
public class ShipLightControl extends LightControl {

    PointLight pointLight;
    Vector3f pos;

    public ShipLightControl(final PointLight pointLight) {
        super(pointLight);

        this.pointLight = pointLight;
    }

    @Override
    public void update(final float tpf) {
        if (enabled && getSpatial() != null && pointLight != null) {
            pos = getSpatial().getWorldTranslation();
            pointLight.setPosition(pos.add(0, 2, 0));
        }
    }
}
