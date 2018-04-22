/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.view;

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
import example.ConnectionState;
import example.Main;
import example.TimeState;
import example.es.BodyPosition;
import example.es.Decay;
import example.es.PointLightComponent;
import example.es.Position;
import java.util.HashMap;

/**
 *
 * @author Asser
 */
public class LightState extends BaseAppState {

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

                float newRadius = Math.max(plc.getRadius() * (float) (1 - (FastMath.pow((float)d.getPercent(), 5f))), 0f);

                pl.setRadius(newRadius);
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
