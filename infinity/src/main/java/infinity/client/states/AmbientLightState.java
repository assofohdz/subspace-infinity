/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

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

    // public static final ColorRGBA DEFAULT_DIFFUSE = ColorRGBA.White.mult(2);
    public static final ColorRGBA DEFAULT_AMBIENT = ColorRGBA.White.mult(0.4f);

    // private VersionedHolder<Vector3f> lightDir = new VersionedHolder<Vector3f>();

    // private ColorRGBA sunColor;
    // private DirectionalLight sun;
    private final ColorRGBA ambientColor;
    private AmbientLight ambient;
    // private float timeOfDay = FastMath.atan2(1, 0.3f) / FastMath.PI;
    // private float inclination = FastMath.HALF_PI - FastMath.atan2(1, 0.4f);
    // private float orientation = 0; //FastMath.HALF_PI;

    private Node rootNode; // the one we added the lights to

    public AmbientLightState() {
        this(FastMath.atan2(1, 0.3f) / FastMath.PI);
    }

    public AmbientLightState(@SuppressWarnings("unused") final float time) {
        // lightDir.setObject(new Vector3f(-0.2f, -1, -0.3f).normalizeLocal());
        ambientColor = DEFAULT_AMBIENT.clone();
    }

    public void setAmbient(final ColorRGBA ambient) {
        ambientColor.set(ambient);
    }

    public ColorRGBA getAmbient() {
        return ambientColor;
    }

    @Override
    protected void initialize(final Application app) {
        ambient = new AmbientLight();
        ambient.setColor(ambientColor);

        // setTimeOfDay(0.05f);
    }

    @Override
    protected void cleanup(final Application app) {
        return;
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
