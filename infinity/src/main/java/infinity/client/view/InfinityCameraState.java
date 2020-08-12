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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.CameraNode;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.CameraControl;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.mathd.trans.PositionTransition3d;
import com.simsilica.mathd.trans.TransitionBuffer;
import com.simsilica.state.CameraState;

import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;

/**
 * A state to manage in-game camera. It simply follows the avatar of the player
 *
 * @author Asser
 */
public class InfinityCameraState extends CameraState {

    WatchedEntity watchedAvatar;
    EntityId avatarId;

    static Logger log = LoggerFactory.getLogger(InfinityCameraState.class);

    public static final float DISTANCETOPLANE = 40;
    private Camera cam;
    private EntityData ed;
    Vector3f viewLoc = new Vector3f(0, DISTANCETOPLANE, 0);
    private TimeSource timeSource;

    ModelViewState viewState;
    Spatial avatarSpatial;
    private GameSessionClientService gameSession;
    private Vector3f avatarPos;
    private float frustumSize = 1;

    private boolean initializedCam = false;

    private CameraNode camNode;

    private InfinityCamControl camControl;
    private GameSessionClientService session;
    private TransitionBuffer<PositionTransition3d> avatarTransBuffer;

    public InfinityCameraState() {
    }

    @Override
    protected void initialize(Application app) {
        this.cam = app.getCamera();

        camNode = new CameraNode("Camera", cam);
        // SpatialToamera means the camera copies movement by the target
        camNode.setControlDir(CameraControl.ControlDirection.SpatialToCamera);

        camControl = new InfinityCamControl(cam, InfinityCamControl.ControlDirection.SpatialToCamera, DISTANCETOPLANE);

        // this.camera.setParallelProjection(true);
        // float aspect = (float) this.camera.getWidth() / this.camera.getHeight();
        // this.camera.setFrustum(-1000, 1000, -aspect * frustumSize, aspect *
        // frustumSize, frustumSize, -frustumSize);
        app.getRenderManager().setCamera(cam, true);

        this.ed = getState(ConnectionState.class).getEntityData();

        this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();

        this.viewState = getState(ModelViewState.class);

        this.session = getState(ConnectionState.class).getService(GameSessionClientService.class);
    }

    @Override
    protected void cleanup(Application app) {
        // Will only happen if we are closing the game
        /*
         * if (watchedAvatar != null) { watchedAvatar.release(); }
         */
    }

    @Override
    public void update(float tpf) {
        long time = timeSource.getTime();
        /*
         * if (avatarSpatial == null && viewState.getAvatarSpatial() != null) {
         * avatarSpatial = viewState.getAvatarSpatial(); } else if (!initializedCam &&
         * avatarSpatial != null) { //avatarSpatial.addControl(camControl);
         *
         * initializedCam = true; }
         */
        // if (initializedCam) {
        // log.info("update:: setting viewLoc to:"+cam.getLocation());
        /*
         * avatarTransBuffer = viewState.getAvatarBuffer(); avatarPos =
         * viewState.getAvatarPosition();
         *
         * if (avatarTransBuffer != null) {
         *
         * PositionTransition3d trans = avatarTransBuffer.getTransition(time); if (trans
         * != null) { Vector3f pos = trans.getPosition(time, true).toVector3f();
         * //log.info("update():: avatarPos = "+avatarPos);
         * getState(WorldViewState.class).setViewLocation(pos);
         *
         * pos.subtractLocal(getState(ModelViewState.class).get)
         *
         * cam.setLocation(pos.add(0, DISTANCETOPLANE, 0)); cam.lookAt(pos,
         * Vector3f.UNIT_Y);
         *
         * //getState(WorldViewState.class).setViewLocation(avatarSpatial.
         * getWorldTranslation()); session.setView(new Quatd(cam.getRotation()), new
         * Vec3d(pos)); } }
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
}
