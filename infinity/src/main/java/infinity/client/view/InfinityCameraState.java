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

import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.ChildPositionTransition3d;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.mathd.GridCell;
import infinity.InfinityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;

import com.jme3.app.Application;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.state.CameraState;

/**
 * A state to manage in-game camera. It simply follows the avatar of the player
 *
 * @author Asser
 */
public class InfinityCameraState extends CameraState {

    static Logger log = LoggerFactory.getLogger(InfinityCameraState.class);

    public static final float DISTANCETOPLANE = 50;
    private TimeSource time;
    private Camera cam;

    private Vector3f initialCamLoc = new Vector3f(20,DISTANCETOPLANE,20);

    private GameSessionClientService session;

    private Spatial spatial;
    private Vector3f newCamPos;
    private boolean initialized;
    private ModelViewState modelView;
    private EntityId avatarId;
    private WatchedEntity self;
    private EntityData ed;

    public InfinityCameraState() {
        super();
    }

    public InfinityCameraState(EntityId avatar, TimeSource timeSource) {
        super();
        this.avatarId = avatar;
        this.time = timeSource;
    }

    public Spatial getSpatial(){
        return spatial;
    }

    @Override
    protected void initialize(final Application app) {
        
        this.ed = getState(ConnectionState.class).getEntityData();
        
        cam = app.getCamera();
        //cam.setLocation(initialCamLoc);
        cam.lookAt(new Vector3f(0,-1,0), Vector3f.UNIT_Y);

        app.getRenderManager().setCamera(cam, true);

        session = getState(ConnectionState.class).getService(GameSessionClientService.class);

        modelView = getState(ModelViewState.class);

        self = ed.watchEntity(avatarId, BodyPosition.class);
        log.info("self:" + self);
        BodyPosition bPos = self.get(BodyPosition.class);
        log.info("self pos:" + bPos);
        if( bPos != null ) {
            // Need to initialize the shared transition buffer
            bPos.initialize(avatarId, 12);
        }
    }

    @Override
    protected void cleanup(final Application app) {
    }

    @Override
    public void update(final float tpf) {
        if( self.applyChanges() ) {
            log.info("self changes");
            BodyPosition bPos = self.get(BodyPosition.class);
            log.info("self pos update:" + bPos);
            if( bPos != null ) {
                // Need to initialize the shared transition buffer
                bPos.initialize(avatarId, 12);
            }
            // Thinking we should wait to be sure we have data
            //updateAvatarPosition(bPos);
        } else {
            BodyPosition bPos = self.get(BodyPosition.class);
            updateAvatarPosition(bPos);
        }

        /*
        if (modelView.isAvatarEnabled()){
            Vector3f loc = getState(WorldViewState.class).getViewLocation(); //camera.getLocation();
            log.info("---------------->");
            log.info("view loc="+loc);
            //This is our new center of view:
            Vector3f avatarLoc = modelView.getAvatarLoc();
            log.info("player spatial world loc:"+avatarLoc);
            avatarLoc.setY(DISTANCETOPLANE);
            log.info("new view loc:"+avatarLoc);
            log.info("<----------------");
            //Update view location on client, this is in world space
            getState(WorldViewState.class).setViewLocation(avatarLoc);

            // Note: right now this is the only thing that actually moves the view on the
            // server.
            session.setView(new Quatd(cam.getRotation()), new Vec3d(avatarLoc));
        }
        */

    }

    private void updateAvatarPosition(BodyPosition bPos) {
        long t = time.getTime();
        ChildPositionTransition3d frame = bPos.getFrame(t);
        if( frame == null ) {
            if( t != 0 ) {
                log.warn("no transition frame for time:" + t);
            }
            return;
        }

        // Only care about position at the moment
        Vec3d v = frame.getPosition(t, true);
        v.addLocal(0, DISTANCETOPLANE, 0);

        getState(WorldViewState.class).setViewLocation(v.toVector3f());
        //viewPosition.setObject(v.clone());

        //Quatd q = frame.getRotation(t, true);
        //viewOrientation.setObject(q.clone());

        // In x/z we need to be relative to the grid
        //GridCell cell = InfinityConstants.PHYSICS_GRID.getContainingCell(v);

        //v.x = v.x - cell.getWorldOrigin().x;
        //v.z = v.z - cell.getWorldOrigin().z;

        //cam.setLocation(v.toVector3f());

        session.setView(new Quatd(cam.getRotation()), v);
    }

    @Override
    public String getId() {
        return "InfinityCameraState";
    }

    @Override
    protected void onEnable() {
        return;
    }

    @Override
    protected void onDisable() {
        return;
    }

    public void setAvatarEntityId(EntityId avatar) {
        this.avatarId = avatar;
    }

    /*
    public void initializeSpatialCamera() {
        spatial = getState(ModelViewState.class).getAvatarSpatial();
        //Add control to make camera follow spatial
        if (spatial != null) {
            InfinityCamControl c = new InfinityCamControl(cam, InfinityCamControl.ControlDirection.SpatialToCamera, DISTANCETOPLANE);
            c.setSpatial(spatial);
            spatial.addControl(c);

            initialized = true;
            setEnabled(true);
        }
    }
     */
}
