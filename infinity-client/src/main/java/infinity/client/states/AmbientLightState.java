// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;

/**
 *
 *
 * @author Paul Speed
 */
public class AmbientLightState extends com.jme3.app.state.BaseAppState {

    public static final ColorRGBA DEFAULT_AMBIENT = ColorRGBA.White.mult(0.05f);

    private final ColorRGBA ambientColor;
    private AmbientLight ambient;

    private Node rootNode; // the one we added the lights to

    public AmbientLightState() {
        this(FastMath.atan2(1, 0.3f) / FastMath.PI);
    }

    public AmbientLightState(@SuppressWarnings("unused") final float time) {
        ambientColor = DEFAULT_AMBIENT.clone();
    }

    public void setAmbient(final ColorRGBA ambient) {
        ambientColor.set(ambient);
        if (this.ambient != null) {
            this.ambient.setColor(ambientColor);
        }
    }

    public ColorRGBA getAmbient() {
        return ambientColor;
    }

    @Override
    protected void initialize(final Application app) {
        ambient = new AmbientLight();
        ambient.setColor(ambientColor);
    }

    @Override
    protected void cleanup(final Application app) {
        // no-op
    }

    @Override
    protected void onEnable() {
        rootNode = ((SimpleApplication) getApplication()).getRootNode();
        rootNode.addLight(ambient);
    }

    @Override
    protected void onDisable() {
        rootNode.removeLight(ambient);
    }
}
