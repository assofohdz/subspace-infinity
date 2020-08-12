/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.client.view;

import com.jme3.light.PointLight;
import com.jme3.math.Vector3f;
import com.jme3.scene.control.LightControl;

/**
 *
 * @author AFahrenholz
 */
public class ShipLightControl extends LightControl {

    PointLight pointLight;
    Vector3f pos;

    public ShipLightControl(PointLight pointLight) {
        super(pointLight);

        this.pointLight = pointLight;
    }

    @Override
    public void update(float tpf) {
        // super.update(tpf);
        if (enabled && getSpatial() != null && pointLight != null) {
            pos = getSpatial().getWorldTranslation();
            pointLight.setPosition(pos.add(0, 2, 0));
        }
    }
}
