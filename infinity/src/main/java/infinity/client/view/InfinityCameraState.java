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
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntityData;
import com.simsilica.es.WatchedEntity;
import com.simsilica.es.base.DefaultWatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.trans.PositionTransition3d;
import com.simsilica.mathd.trans.TransitionBuffer;
import com.simsilica.state.CameraState;
import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;
import infinity.es.BodyPosition;
import infinity.net.GameSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A state to manage in-game camera. It simply follows the avatar of the player
 *
 * @author Asser
 */
public class InfinityCameraState extends CameraState{

    //WatchedEntity watchedAvatar;
    EntityId avatarId;

    static Logger log = LoggerFactory.getLogger(InfinityCameraState.class);

    public static final float DISTANCETOPLANE = 40;
    private Camera camera;
    private EntityData ed;
    Vector3f cameraPos = new Vector3f(0, DISTANCETOPLANE, 0);
    Vector3f lastAvatarLoc = new Vector3f();
    private TimeSource timeSource;

    ModelViewState viewState;
    Spatial avatarSpatial;
    private GameSessionClientService gameSession;
    private Vector3f avatarPos;
    private TransitionBuffer<PositionTransition3d> buffer;
    private float frustumSize = 1;

    public InfinityCameraState() {
    }

    @Override
    protected void initialize(Application app) {
        this.camera = app.getCamera();

        this.camera.setLocation(cameraPos);
        this.camera.lookAt(new Vector3f(), Vector3f.UNIT_Y); //Set camera to look at the origin
        //this.camera.setParallelProjection(true);
        //float aspect = (float) this.camera.getWidth() / this.camera.getHeight();
        //this.camera.setFrustum(-1000, 1000, -aspect * frustumSize, aspect * frustumSize, frustumSize, -frustumSize);

        app.getRenderManager().setCamera(camera, true);

        this.ed = getState(ConnectionState.class).getEntityData();

        this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();

        this.viewState = getState(ModelViewState.class);
    }

    @Override
    protected void cleanup(Application app) {
        //Will only happen if we are closing the game
        /*if (watchedAvatar != null) {
            watchedAvatar.release();
        }*/
    }

    @Override
    public void update(float tpf) {
        long time = timeSource.getTime();
        //watchedAvatar.applyChanges();

        /*

        //This means our avatar has a new shapeinfo (new spatial)
        if (watchedAvatar.applyChanges() || avatarSpatial == null) {
            avatarSpatial = viewState.getModelSpatial(avatar, true);
        }
        avatarModel = viewState.getModel(avatar, false);
        avatarModel.
        avatarPos = avatarSpatial.getWorldTranslation().clone();

        Quaternion rot = camera.getRotation();

        cameraPos = avatarPos.clone().setY(40);

        gameSession.setView(new Quatd(rot), new Vec3d(avatarPos));
        getState(WorldViewState.class).setViewLocation(cameraPos, avatarPos);

        log.info("Camera is at: " + cameraPos + ", looking at :" + avatarPos);
         */
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
        this.gameSession = getState(ConnectionState.class).getService(GameSessionClientService.class);
    }

    @Override
    protected void onDisable() {

    }

    /*@Override
    public void setAvatar(EntityId avatarId) {
        this.avatarId = avatarId;
        watchedAvatar = ed.watchEntity(avatarId, BodyPosition.class);
    }*/
}
