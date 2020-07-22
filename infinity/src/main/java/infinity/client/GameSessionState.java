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

import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.texture.plugins.AWTLoader;
//import com.jme3.math.Vector3f;

//import com.simsilica.lemur.GuiGlobals;
//import com.simsilica.lemur.input.InputMapper;
import com.simsilica.builder.BuilderState;
import com.simsilica.es.EntityId;
import com.simsilica.lemur.event.MouseAppState;
import com.simsilica.state.CompositeAppState;
import infinity.HelpState;
import infinity.HostState;
import infinity.SettingsState;
import infinity.TimeState;
import infinity.client.ChatState;
import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;
import infinity.client.view.InfinityCameraState;
import infinity.client.view.ModelViewState;
import infinity.client.view.PhysicsDebugState;
import infinity.client.view.SkyState;
import infinity.client.view.WorldViewState;

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

    private boolean hostIsLocal = false;

    public GameSessionState() {
        super(
                new AvatarMovementState(),
                new TimeState(), // Has to be before any visuals that might need it.
                //new CameraMovementState(),
                //new CameraState(),
                //new MovementState(), - is added later with reference to MovementTarget
                //new CameraState(),
                //new LightingState(), //For the general lighting
                //new AmbientLightState(),
                //new PostProcessingState(),
                //new GridState(new Grid(32, 0, 32)),
                //new SkySettingsState(),
                new SkyState(),

                //new infinity.client.view.SkyState(),

                new BuilderState(4, 4),
                new WorldViewState(),
                new ModelViewState(),
        //For now we do everything unshaded
        //new LightState() //For pointlights and decaying lights - must come after ModelViewState because we need the spatials to be there
        new InfinityCameraState() //Add camera last
        );

        addChild(new HelpState(), true);
        addChild(new SettingsState(), true);
        addChild(new ChatState(), true);
        //addChild(new MapState(), true);
        //addChild(new ToolState(), true);
    }

    @Override
    protected void initialize(Application app) {
        //com.simsilica.mworld.World world = getState(ConnectionState.class).getService(com.simsilica.mworld.net.client.WorldClientService.class);
        //log.info("World:" + world);

        //com.simsilica.mworld.LeafData data = world.getLeaf(0);
        //log.info("Data for leafId 0:" + data);
        //data = world.getLeaf(new Vec3i(0, 2, 0));
        //log.info("Data for leaf 0, 2, 0:" + data);
        //getState(CameraState.class).setFieldOfView(60);
        EntityId avatar = getState(ConnectionState.class).getService(GameSessionClientService.class).getAvatar();
        // See if this is local host mode.  This stuff should maybe be moved
        // to its own debug manager state. 
        HostState host = getState(HostState.class);
        if (host != null) {
            addChild(new PhysicsDebugState(host), true);
            //hostIsLocal = true;
            // Then we can add some debug states
            //addChild(new BinStatusState(host.getSystems().get(PhysicsSpace.class), 64)); 
            //addChild(new BodyDebugState(host.getSystems().get(MPhysSystem.class)));
            //addChild(new ContactDebugState(host.getSystems().get(PhysicsSpace.class)));
        }

        //Camera should be set to orthogonal
        //getState(InfinityCameraState.class).setAvatar(avatar);
        //getState(InfinityCameraState.class).setFieldOfView(60);
        //Modelview state
        getState(ModelViewState.class).setAvatar(avatar);

        getState(TimeState.class).setTimeSource(getState(ConnectionState.class).getRemoteTimeSource());
        
        
        this.getApplication().getAssetManager().registerLoader(AWTLoader.class, "bm2");
        addChild(new MouseAppState(this.getApplication()));
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        /*if( hostIsLocal ) {
            InputMapper input = GuiGlobals.getInstance().getInputMapper();
            input.addDelegate(DebugFunctions.F_BIN_DEBUG, getState(BinStatusState.class), "toggleEnabled");
            input.addDelegate(DebugFunctions.F_BODY_DEBUG, getState(BodyDebugState.class), "toggleEnabled"); 
            input.addDelegate(DebugFunctions.F_CONTACT_DEBUG, getState(ContactDebugState.class), "toggleEnabled");
        }*/
    }

    /*public void update( float tpf ) {
        //log.info("update");
        
        // This update() is called after the children.
        BinStatusState binState = getState(BinStatusState.class); 
        if( binState != null ) {
            Vector3f loc = getState(WorldViewState.class).getViewLocation();
            binState.setViewOrigin(loc.x, 0, loc.z);
            BodyDebugState bodyState = getState(BodyDebugState.class);
            bodyState.setViewOrigin(loc.x, 0, loc.z);
            ContactDebugState contactState = getState(ContactDebugState.class);
            contactState.setViewOrigin(loc.x, 0, loc.z);
        }        
         
    }*/
    @Override
    protected void onDisable() {
        /*if( hostIsLocal ) {
            InputMapper input = GuiGlobals.getInstance().getInputMapper();
            input.removeDelegate(DebugFunctions.F_BIN_DEBUG, getState(BinStatusState.class), "toggleEnabled");
            input.removeDelegate(DebugFunctions.F_BODY_DEBUG, getState(BodyDebugState.class), "toggleEnabled"); 
            input.removeDelegate(DebugFunctions.F_CONTACT_DEBUG, getState(ContactDebugState.class), "toggleEnabled");
        }*/
    }
}
