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
package infinity.client.audio;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import com.jme3.audio.Listener;
import com.jme3.audio.plugins.WAVLoader;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;

import infinity.TimeState;
import infinity.client.ConnectionState;
import infinity.es.AudioType;
import infinity.es.AudioTypes;
import infinity.es.BodyPosition;
import infinity.es.Parent;

/**
 *
 * @author Asser
 */
public class AudioState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(AudioState.class);

    private final static float FULLVOLUMEDISTANCE = 10f;
    private final static float ZEROVOLUMEDISTANCE = 30f;

    private TimeState timeState;
    private EntityData ed;
    private EntitySet audio;
    private AssetManager assets;
    private Listener listener;
    private final SIAudioFactory factory;
    private AudioContainer sounds;
    private Map<EntityId, AudioNode> soundIndex = new HashMap<>();
    private Node soundRoot;

    private long time;

    public AudioState(final SIAudioFactory factory) {
        this.factory = factory;

        log.debug("Constructed AudioState");
    }

    @Override
    protected void initialize(final Application app) {
        factory.setState(this);
        timeState = getState(TimeState.class);
        ed = getState(ConnectionState.class).getEntityData();

        // This state just needs to know which sounds to play and where to play them
        audio = ed.getEntities(AudioType.class, BodyPosition.class);

        // Get asset manager to be able to retrieve the sounds
        assets = app.getAssetManager();
        // Register default wave loader with the wa2 extension
        assets.registerLoader(WAVLoader.class, "wa2");
        listener = app.getListener();

        soundRoot = new Node();
        soundIndex = new HashMap<>();

    }

    @Override
    protected void cleanup(final Application app) {
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
    public void update(final float tpf) {
        // Move listener with camera
        // listener.setLocation(cam.getLocation());
        // listener.setRotation(cam.getRotation());

        // Grab a consistent time for this frame
        time = timeState.getTime();

        sounds.update();
    }

    /**
     * Contains all playing and future sounds
     */
    private class AudioContainer extends EntityContainer<Spatial> {

        public AudioContainer(final EntityData ed) {
            super(ed, AudioType.class, Parent.class);
        }

        @Override
        protected Spatial addObject(final Entity e) {
            final Spatial result = createAudio(e);
            updateObject(result, e);
            return result;
        }

        @Override
        protected void updateObject(final Spatial object, final Entity e) {
            updateSound(object, e, true);
        }

        @Override
        protected void removeObject(final Spatial object, final Entity e) {
            removeSound(object, e);
        }
    }

    protected void removeSound(final Spatial spatial, final Entity entity) {
        soundIndex.remove(entity.getId());
        spatial.removeFromParent();
    }

    protected void updateSound(final Spatial spatial, final Entity entity, final boolean updatePosition) {
        if (updatePosition) {
            final Parent p = entity.get(Parent.class);
            if (p.getParentEntity().getId() == 0L) {
                // No position to update to
            } else {
                final BodyPosition pos = ed.getComponent(p.getParentEntity(), BodyPosition.class);

                // I like to move it... move it...
                spatial.setLocalTranslation(pos.getLastLocation().toVector3f());
                spatial.setLocalRotation(pos.getLastOrientation().toQuaternion());
            }

        }
    }

    protected Spatial createAudio(final Entity entity) {

        // Check to see if one already exists
        AudioNode result = soundIndex.get(entity.getId());
        if (result != null) {
            result.playInstance();
            return result;
        }

        // Else figure out what type to create...
        final AudioType type = entity.get(AudioType.class);
        final String typeName = type.getTypeName(ed);
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
        case AudioTypes.EXPLOSION2:
            result = createExplosion2(entity);
            break;
        case AudioTypes.BURST:
            result = createBurst(entity);
            break;
        case AudioTypes.REPEL:
            result = createRepel(entity);
            break;
        default:
            throw new RuntimeException("Unknown spatial type:" + typeName);
        }

        // Add it to the index
        soundIndex.put(entity.getId(), result);
        soundRoot.attachChild(result);

        final float volume = 1f;
        /*
         * if (result != null) { BodyPosition bp = ed.getComponent(entity.getId(),
         * BodyPosition.class); Position pos = ed.getComponent(entity.getId(),
         * Position.class); if (bp != null) {
         *
         * PositionTransition trans = bp.getBuffer().getTransition(time); if (trans !=
         * null) { Vector3f bodyPositionPos = trans.getPosition(time, true); volume =
         * this.calculateVolume(bodyPositionPos); } } else if (pos != null) { volume =
         * this.calculateVolume(pos.getLocation().toVector3f()); }
         *
         * result.setVolume(volume); result.playInstance(); } else { throw new
         * NullPointerException("Resulting audio node is null"); }
         */
        if (result != null) {

            // result.setref
            result.playInstance();
        }
        return result;
    }

    private AudioNode createPickUpPrize(final Entity entity) {
        // Node information:
        final Node result = new Node("pickupPrize:" + entity.getId());
        result.setUserData("pickupPrizeId", entity.getId().getId());
        // result.setUserData(LayerComparator.LAYER, 1);

        // Spatial information:
        final AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createFireThor(final Entity entity) {
        // Node information:
        final Node result = new Node("fireThor:" + entity.getId());
        result.setUserData("fireThorId", entity.getId().getId());
        // result.setUserData(LayerComparator.LAYER, 1);

        // Spatial information:
        final AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createFireBombs(final Entity entity) {
        // Node information:
        final Node result = new Node("fireBombs:" + entity.getId());
        result.setUserData("fireBombsId", entity.getId().getId());
        // result.setUserData(LayerComparator.LAYER, 1);

        // Spatial information:
        final AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createFireGravBomb(final Entity entity) {
        // Node information:
        final Node result = new Node("fireGravBomb:" + entity.getId());
        result.setUserData("fireGravBombId", entity.getId().getId());
        // result.setUserData(LayerComparator.LAYER, 1);

        // Spatial information:
        final AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createRepel(final Entity entity) {
        // Node information:
        final Node result = new Node("repel:" + entity.getId());
        result.setUserData("repelId", entity.getId().getId());
        // result.setUserData(LayerComparator.LAYER, 1);

        // Spatial information:
        final AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createFireGuns(final Entity entity) {
        // Node information:
        final Node result = new Node("fireGuns:" + entity.getId());
        result.setUserData("fireGunsId", entity.getId().getId());
        // result.setUserData(LayerComparator.LAYER, 1);

        // Spatial information:
        final AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createExplosion2(final Entity entity) {
        // Node information:
        final Node result = new Node("explosion2:" + entity.getId());
        result.setUserData("explosion2Id", entity.getId().getId());
        // result.setUserData(LayerComparator.LAYER, 1);

        // Spatial information:
        final AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }

    private AudioNode createBurst(final Entity entity) {
        // Node information:
        final Node result = new Node("burstSound:" + entity.getId());
        result.setUserData("burstSoundId", entity.getId().getId());
        // result.setUserData(LayerComparator.LAYER, 1);

        // Spatial information:
        final AudioNode an = factory.createAudio(entity);
        result.attachChild(an);

        return an;
    }
}
