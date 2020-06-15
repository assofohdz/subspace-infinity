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

import com.jme3.app.Application;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntityData;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.state.CameraState;
import infinity.client.ConnectionState;
import infinity.net.GameSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A state to manage in-game camera. It simply follows the avatar of the player
 *
 * @author Asser
 */
public class InfinityCameraState extends CameraState implements GameSessionListener {

    WatchedEntity watchedAvatar;
    EntityId avatar;

    static Logger log = LoggerFactory.getLogger(InfinityCameraState.class);

    public static final float DISTANCETOPLANE = 40;
    private Camera camera;
    private EntityData ed;
    Vector3f newCameraLoc = new Vector3f(0, DISTANCETOPLANE, 0);
    Vector3f lastAvatarLoc = new Vector3f();
    private TimeSource timeSource;

    ModelViewState viewState;
    Spatial avatarSpatial;

    public InfinityCameraState() {
    }

    @Override
    protected void initialize(Application app) {
        this.camera = app.getCamera();

        this.camera.setLocation(newCameraLoc);
        this.camera.lookAt(lastAvatarLoc, Vector3f.UNIT_Y); //Set camera to look at the origin

        this.ed = getState(ConnectionState.class).getEntityData();

        this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();

        this.viewState = getState(ModelViewState.class);
    }

    @Override
    protected void cleanup(Application app) {
        //Will only happen if we are closing the game
        if (watchedAvatar != null) {
            watchedAvatar.release();
        }
    }

    @Override
    public void update(float tpf) {
        if (avatarSpatial == null) {
            avatarSpatial = viewState.getModelSpatial(avatar, true);
        } else {
            Vector3f newCamPos = avatarSpatial.getWorldTranslation();

            lastAvatarLoc = newCamPos;
            newCameraLoc.x = lastAvatarLoc.x;
            //We dont set the y because that doesnt change (for now)
            newCameraLoc.z = lastAvatarLoc.z;

            getState(WorldViewState.class).setViewLocation(newCameraLoc, lastAvatarLoc);
        }

        /*
        BodyPosition bp2 = ed.getComponent(avatar, BodyPosition.class);

        if (bp2 != null) {
            long time = this.timeSource.getTime();
            PositionTransition3d transition = bp2.getBuffer().getTransition(time);
            if (transition != null) {
                Vector3f pos = transition.getPosition(time, true).toVector3f();
           
                lastAvatarLoc = pos;
                newCameraLoc.x = lastAvatarLoc.x;
                //We dont set the y because that doesnt change (for now)
                newCameraLoc.z = lastAvatarLoc.z;

                camera.setLocation(newCameraLoc);
                camera.lookAt(lastAvatarLoc, Vector3f.UNIT_Y);
            }
        }
         */
    }

    @Override
    public String getId() {
        return "InfinityCameraState";
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    public void setAvatar(EntityId avatarId) {
        //We only need to know where the player is currently - that's where we'll point our camera 
        this.avatar = avatarId;
        //Doesn't work:
        //watchedAvatar = ed.watchEntity(this.avatar, BodyPosition.class);
    }
}
