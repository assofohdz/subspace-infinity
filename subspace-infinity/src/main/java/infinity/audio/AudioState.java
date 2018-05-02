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
package infinity.audio;

import com.google.common.collect.HashBiMap;
import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import com.jme3.audio.Listener;
import com.jme3.audio.plugins.WAVLoader;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import infinity.ConnectionState;
import infinity.Main;
import infinity.TimeState;
import infinity.api.es.AudioType;
import infinity.api.es.AudioTypes;
import infinity.api.es.Parent;
import infinity.api.es.Position;
import infinity.view.CameraState;
import infinity.view.ModelViewState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 *
 * @author Asser
 */
public class AudioState extends BaseAppState {

    private TimeState timeState;
    private EntityData ed;
    private EntitySet audio;
    private AssetManager assets;
    private Listener listener;
    private Camera cam;
    private final SIAudioFactory factory;
    private ModelViewState viewState;
    private AudioContainer sounds;
    private Map<EntityId, AudioNode> soundIndex = new HashMap<>();
    private Node soundRoot;

    public AudioState(SIAudioFactory factory) {
        this.factory = factory;
    }

    @Override
    protected void initialize(Application app) {
        this.factory.setState(this);
        this.timeState = getState(TimeState.class);
        this.ed = getState(ConnectionState.class).getEntityData();

        //This state just needs to know which sounds to play and where to play them
        this.audio = ed.getEntities(AudioType.class, Position.class);

        //Get asset manager to be able to retrieve the sounds
        this.assets = app.getAssetManager();
        //Register default wave loader with the wa2 extension
        this.assets.registerLoader(WAVLoader.class, "wa2");
        this.listener = app.getListener();
        this.cam = getState(CameraState.class).getCamera();

        this.viewState = getState(ModelViewState.class);

        this.soundRoot = new Node();
        this.soundIndex = new HashMap<>();

    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        sounds = new AudioContainer(ed);
        sounds.start();

        ((SimpleApplication) getApplication()).getRootNode().attachChild(soundRoot);
    }

    @Override
    protected void onDisable() {
        sounds.stop();
        sounds = null;
    }

    @Override
    public void update(float tpf) {
        //Move listener with camera
        listener.setLocation(cam.getLocation());
        listener.setRotation(cam.getRotation());

        // Grab a consistent time for this frame
        long time = timeState.getTime();

        sounds.update();
    }

    /**
     * Contains all playing and future sounds
     */
    private class AudioContainer extends EntityContainer<Spatial> {

        public AudioContainer(EntityData ed) {
            super(ed, AudioType.class, Parent.class);
        }

        @Override
        protected Spatial addObject(Entity e) {
            Spatial result = createAudio(e);
            updateObject(result, e);
            return result;
        }

        @Override
        protected void updateObject(Spatial object, Entity e) {
            updateSound(object, e, true);
        }

        @Override
        protected void removeObject(Spatial object, Entity e) {
            removeSound(object, e);
        }
    }

    protected void removeSound(Spatial spatial, Entity entity) {
        soundIndex.remove(entity.getId());
        spatial.removeFromParent();
    }

    protected void updateSound(Spatial spatial, Entity entity, boolean updatePosition) {
        if (updatePosition) {
            Parent p = entity.get(Parent.class);
            if (p.getParentEntity().getId() == 0l) {
                //No position to update to
            } else {
                Position pos = ed.getComponent(p.getParentEntity(), Position.class);

                // I like to move it... move it...
                spatial.setLocalTranslation(pos.getLocation().toVector3f());
                spatial.setLocalRotation(pos.getFacing().toQuaternion());
            }

        }
    }

    protected Spatial createAudio(Entity entity) {

        // Check to see if one already exists
        AudioNode result = soundIndex.get(entity.getId());
        if (result != null) {
            result.playInstance();
            return result;
        }

        // Else figure out what type to create... 
        AudioType type = entity.get(AudioType.class);
        String typeName = type.getTypeName(ed);
        switch (typeName) {
            case AudioTypes.FIRE_THOR:
                result = createFireThor(entity);
                break;
            case AudioTypes.PICKUP_PRIZE:
                result = createPickUpPrize(entity);
                break;
            case AudioTypes.FIRE_BOMBS_L1:
            case AudioTypes.FIRE_BOMBS_L2:
            case AudioTypes.FIRE_BOMBS_L3:
            case AudioTypes.FIRE_BOMBS_L4:
                result = createFireBombs(entity);
                break;
            case AudioTypes.FIRE_GRAVBOMB:
                result = createFireGravBomb(entity);
                break;
            case AudioTypes.FIRE_GUNS_L1:
            case AudioTypes.FIRE_GUNS_L2:
            case AudioTypes.FIRE_GUNS_L3:
            case AudioTypes.FIRE_GUNS_L4:
                result = createFireGuns(entity);
                break;
            default:
                throw new RuntimeException("Unknown spatial type:" + typeName);
        }

        // Add it to the index
        soundIndex.put(entity.getId(), result);
        soundRoot.attachChild(result);

        if (result != null) {
            result.playInstance();
        } else {
            throw new NullPointerException("Resulting audio node is null");
        }

        return result;
    }

    private AudioNode createPickUpPrize(Entity entity) {
        //Node information:
        Node result = new Node("pickupPrize:" + entity.getId());
        result.setUserData("pickupPrizeId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createFireThor(Entity entity) {
        //Node information:
        Node result = new Node("fireThor:" + entity.getId());
        result.setUserData("fireThorId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createFireBombs(Entity entity) {
        //Node information:
        Node result = new Node("fireBombs:" + entity.getId());
        result.setUserData("fireBombsId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createFireGravBomb(Entity entity) {
        //Node information:
        Node result = new Node("fireGravBomb:" + entity.getId());
        result.setUserData("fireGravBombId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createFireGuns(Entity entity) {
        //Node information:
        Node result = new Node("fireGuns:" + entity.getId());
        result.setUserData("fireGunsId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }
}
