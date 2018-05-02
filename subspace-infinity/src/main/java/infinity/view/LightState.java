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
package infinity.view;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.trans.PositionTransition;
import com.simsilica.mathd.trans.TransitionBuffer;
import infinity.ConnectionState;
import infinity.Main;
import infinity.TimeState;
import infinity.api.es.BodyPosition;
import infinity.api.es.Decay;
import infinity.api.es.PointLightComponent;
import infinity.api.es.Position;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Asser
 */
public class LightState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(LightState.class);

    private EntityData ed;
    private EntitySet movingPointLights;
    private Node rootNode;
    private HashMap<EntityId, PointLight> pointLightMap = new HashMap<>();
    private HashMap<EntityId, TransitionBuffer<PositionTransition>> bufferMap = new HashMap<>();
    private TimeState timeState;
    private Vector3f pointLightOffset = new Vector3f(0, 0, 5);
    private EntitySet decayingPointLights;

    @Override
    protected void initialize(Application app) {

        this.ed = getState(ConnectionState.class).getEntityData();

        this.movingPointLights = ed.getEntities(PointLightComponent.class, BodyPosition.class); //Moving point lights
        this.decayingPointLights = ed.getEntities(PointLightComponent.class, Decay.class); //Lights that decay

        this.timeState = getState(TimeState.class);
    }

    @Override
    protected void cleanup(Application app) {
        this.movingPointLights.release();
        this.movingPointLights = null;
    }

    @Override
    protected void onEnable() {
        rootNode = ((Main) getApplication()).getRootNode();

        //Add a central light to the scene
        //PointLight pl = new PointLight(new Vector3f(0, 0, 10), ColorRGBA.White, 1000);
        //rootNode.addLight(pl);
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
                this.createPointLight(e);
            }

            for (Entity e : movingPointLights.getRemovedEntities()) {
                this.removePointLight(e);
            }
        }

        for (Entity e : movingPointLights) {
            this.updatePointLight(e, time);
        }

        decayingPointLights.applyChanges();

        for (Entity e : decayingPointLights) {

            if (pointLightMap.containsKey(e.getId())) {
                PointLightComponent plc = e.get(PointLightComponent.class);
                Decay d = e.get(Decay.class);

                PointLight pl = pointLightMap.get(e.getId());

                double percentage = d.getPercent();
                float factor = Math.max((float) (1 - (FastMath.pow((float)percentage, 5f))),0f);
                
                pl.setColor(plc.getColor().mult(factor));
                
            }
        }
    }

    private void removePointLight(Entity e) {
        PointLightComponent lt = e.get(PointLightComponent.class);
        PointLight pl = pointLightMap.remove(e.getId());
        rootNode.removeLight(pl);

    }

    //Not working yet
    private void updatePointLight(Entity e, long time) {
        PointLight pl = pointLightMap.get(e.getId());
        TransitionBuffer<PositionTransition> buffer = bufferMap.get(e.getId());
        //        Vec3d location = p.getLocation();
        // Look back in the brief history that we've kept and
        // pull an interpolated value.  To do this, we grab the
        // span of time that contains the time we want.  PositionTransition
        // represents a starting and an ending pos+rot over a span of time.
        PositionTransition trans = buffer.getTransition(time);
        if (trans != null) {
            pl.setPosition(trans.getPosition(time, true).add(pointLightOffset));
        }

    }

    private void createPointLight(Entity e) {
        PointLightComponent plc = e.get(PointLightComponent.class);
        BodyPosition bp = e.get(BodyPosition.class);

        PointLight pl = new PointLight();
        pl.setColor(plc.getColor());
        pl.setRadius(plc.getRadius());

        pointLightMap.put(e.getId(), pl);

        //Create pointer to the threadsafe same position buffer array as modelviewstate. 
        //Ensure it is the same by initialising it everywhere it's used
        bp.initialize(e.getId(), 12);
        bufferMap.put(e.getId(), bp.getBuffer());

        rootNode.addLight(pl);
    }
}
