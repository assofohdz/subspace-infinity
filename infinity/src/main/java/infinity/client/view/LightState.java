/*
 * Copyright (c) 2018, Asser Fahrenholz
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
package infinity.client.view;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.PointLight;
import com.jme3.scene.LightNode;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.mathd.trans.PositionTransition3d;
import com.simsilica.mathd.trans.TransitionBuffer;

import infinity.Main;
import infinity.TimeState;
import infinity.client.ConnectionState;
import infinity.es.BodyPosition;
import infinity.es.PointLightComponent;

/**
 *
 * @author Asser
 */
public class LightState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(LightState.class);

    private EntityData ed;
    private EntitySet movingPointLights, decayingPointLights;
    private Node rootNode;
    private HashMap<EntityId, PointLight> pointLightMap = new HashMap<>();
    private HashMap<EntityId, TransitionBuffer<PositionTransition3d>> bufferMap = new HashMap<>();
    private TimeState timeState;

    public LightState() {
        log.debug("Constructed LightState");
    }

    @Override
    protected void initialize(Application app) {

        ed = getState(ConnectionState.class).getEntityData();

        movingPointLights = ed.getEntities(PointLightComponent.class, BodyPosition.class); // Moving point lights
        decayingPointLights = ed.getEntities(PointLightComponent.class, Decay.class); // Lights that decay

        timeState = getState(TimeState.class);
    }

    @Override
    protected void cleanup(Application app) {
        movingPointLights.release();
        movingPointLights = null;
    }

    @Override
    protected void onEnable() {
        rootNode = ((Main) getApplication()).getRootNode();

        // Add a central light to the scene
        // PointLight pl = new PointLight(new Vector3f(0, 2, 0), ColorRGBA.White, 1000);
        // rootNode.addLight(pl);
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void update(float tpf) {
        // Grab a consistent time for this frame
        long time = timeState.getTime();

        movingPointLights.applyChanges();

        if (movingPointLights.hasChanges()) {
            for (Entity e : movingPointLights.getAddedEntities()) {
                createPointLight(e);
            }

            for (Entity e : movingPointLights.getRemovedEntities()) {
                removePointLight(e);
            }
        }
        /*
         * for (Entity e : movingPointLights) { this.updatePointLight(e, time); }
         */
        decayingPointLights.applyChanges();

        for (Entity e : decayingPointLights) {

            if (pointLightMap.containsKey(e.getId())) {
                PointLightComponent plc = e.get(PointLightComponent.class);
                Decay d = e.get(Decay.class);

                PointLight pl = pointLightMap.get(e.getId());

                // double percentage = 1-d.getPercentRemaining(time);
                // float factor = Math.max((float) (1 - (FastMath.pow((float)percentage,
                // 5f))),0f);
                float percentageRemFloat = (float) d.getPercentRemaining(time);

                pl.setColor(plc.getColor().mult(percentageRemFloat));

            }
        }
    }

    private void removePointLight(Entity e) {
        PointLightComponent lt = e.get(PointLightComponent.class);
        PointLight pl = pointLightMap.remove(e.getId());
        rootNode.removeLight(pl);

    }

    /*
     * //Not working yet
     *
     * @Deprecated private void updatePointLight(Entity e, long time) { PointLight
     * pl = pointLightMap.get(e.getId()); TransitionBuffer<PositionTransition3d>
     * buffer = bufferMap.get(e.getId());
     *
     * // Vec3d location = p.getLocation(); // Look back in the brief history that
     * we've kept and // pull an interpolated value. To do this, we grab the // span
     * of time that contains the time we want. PositionTransition // represents a
     * starting and an ending pos+rot over a span of time. PositionTransition3d
     * trans = buffer.getTransition(time);
     *
     * if (trans != null) { Vector3f res = trans.getPosition(time,
     * true).add(pointLightOffset).toVector3f(); System.out.println("Light pos: " +
     * res); pl.setPosition(res); }
     *
     * }
     */

    private void createPointLight(Entity e) {
        Spatial s = getState(ModelViewState.class).getModelSpatial(e.getId(), true).getParent();

        PointLightComponent plc = e.get(PointLightComponent.class);
        BodyPosition bp = e.get(BodyPosition.class);

        PointLight pl = new PointLight();
        pl.setColor(plc.getColor());
        pl.setRadius(plc.getRadius());
        // Set the pointlights starting position and offset it
        pl.setPosition(s.getWorldTranslation().add(plc.getOffset().toVector3f()));

        pointLightMap.put(e.getId(), pl);

        // Create pointer to the threadsafe same position buffer array as
        // modelviewstate.
        // Ensure it is the same by initialising it everywhere it's used
        bp.initialize(e.getId(), 12);
        bufferMap.put(e.getId(), bp.getBuffer());

        rootNode.addLight(pl);
        LightNode ln = new LightNode(e.getId().toString(), pl);
        // s.getp.attachChild(ln);
    }
}
