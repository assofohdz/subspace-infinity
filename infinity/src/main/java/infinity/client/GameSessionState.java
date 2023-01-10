/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.client;

import com.jme3.math.ColorRGBA;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.mathd.Grid;
import infinity.*;
import infinity.client.states.*;
import infinity.client.view.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.texture.plugins.AWTLoader;
//import com.jme3.math.Vector3f;

//import com.simsilica.lemur.GuiGlobals;
//import com.simsilica.lemur.input.InputMapper;
import com.simsilica.builder.BuilderState;
import com.simsilica.es.EntityId;
import com.simsilica.state.CompositeAppState;

//import com.simsilica.mphys.PhysicsSpace;
//import com.simsilica.ext.mphys.MPhysSystem;
//import com.simsilica.ext.mphys.debug.*;
/**
 *
 *
 * @author Paul Speed
 */
public class GameSessionState extends CompositeAppState {

    static Logger log = LoggerFactory.getLogger(GameSessionState.class);
    private boolean hostIsLocal;
    private TimeSource timeSource;

    // private final boolean hostIsLocal = false;

    public GameSessionState() {
        super(
                //new CameraMovementState(),
                new AvatarMovementState(),
                //new CameraState(),
                //new LightingState(),
                new TimeState(), // Has to be before any visuals that might need it.
                new SkyState(),
                // new PostProcessingState(),
                // new SkySettingsState(),
                new BuilderState(4, 4),
                new WorldViewState(),
                new ModelViewState(),
                //new GridState(new Grid(InfinityConstants.GRID_CELL_SIZE)),
                new SpaceGridState(InfinityConstants.GRID_CELL_SIZE, 2,  new ColorRGBA(0.8f, 1f, 1f, 0.5f)),
                new LightState()//,
                //new AmbientLightState()
        );

        addChild(new HelpState(), true);
        addChild(new SettingsState(), true);
        addChild(new ChatState(), true);

        addChild(new MapState(), true);
        // addChild(new ToolState(), true);

    }

    @Override
    protected void initialize(final Application app) {
        // com.simsilica.mworld.World world =
        // getState(ConnectionState.class).getService(com.simsilica.mworld.net.client.WorldClientService.class);
        // log.info("World:" + world);

        // com.simsilica.mworld.LeafData data = world.getLeaf(0);
        // log.info("Data for leafId 0:" + data);
        // data = world.getLeaf(new Vec3i(0, 2, 0));
        // log.info("Data for leaf 0, 2, 0:" + data);
        // getState(CameraState.class).setFieldOfView(60);
        final EntityId avatar = getState(ConnectionState.class).getService(GameSessionClientService.class).getAvatar();
        // See if this is local host mode. This stuff should maybe be moved
        // to its own debug manager state.
        final HostState host = getState(HostState.class);
        if (host != null) {
            //addChild(new PhysicsDebugState(host), true);
            hostIsLocal = true;
            // Then we can add some debug states
            //addChild(new BinStatusState(host.getSystems().get(PhysicsSpace.class), 0));
            //addChild(new BodyDebugState(host.getSystems().get(MPhysSystem.class)));
            //addChild(new ContactDebugState(host.getSystems().get(PhysicsSpace.class)));
        }

        this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();

        getState(TimeState.class).setTimeSource(this.timeSource);

        InfinityCameraState cameraState = new InfinityCameraState(avatar, timeSource);
        addChild(cameraState);
        // Camera should be set to orthogonal
        // getState(InfinityCameraState.class).setAvatar(avatar);
        // getState(InfinityCameraState.class).setFieldOfView(60);
        // Modelview state

        getState(ModelViewState.class).setAvatarEntityId(avatar);

        //getState(TimeState.class).setTimeSource(getState(ConnectionState.class).getRemoteTimeSource());

        getApplication().getAssetManager().registerLoader(AWTLoader.class, "bm2");

        //addChild(new MouseAppState(getApplication()));

        //addChild(new MiniMapState(((SimpleApplication) getApplication()).getRootNode(), 64, 200));
    }

    @Override
    protected void cleanup(final Application app) {
        return;
    }

    @Override
    protected void onEnable() {

    }

    public void update( float tpf ) {

    }
    @Override
    protected void onDisable() {

    }
}
