/*
 * $Id$
 * 
 * Copyright (c) 2021, Simsilica, LLC
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

package infinity.sim.ai;

import infinity.es.MobType;
import infinity.es.ProbeInfo;
import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.*;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.*;
import com.jme3.scene.shape.Sphere;

import com.simsilica.es.*;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.mathd.*;
import com.simsilica.sim.GameSystemManager;
import com.simsilica.state.DebugHudState;

import com.simsilica.mphys.*;

/**
 *
 *
 *  @author    Paul Speed
 */
public class MobDebugState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(MobDebugState.class);

    private GameSystemManager systems;

    private EntityData ed;
    private MobSystem mobs;   
    private MobStats stats;
    private PhysicsSpace space;
    
    private VersionedHolder<String> frameTime;
    private VersionedHolder<String> activeMobCount;

    /**
     *  Keep track of the origin under which the camera operates.
     *  viewOrigin.add(camera.getLocation()) should be the world position of
     *  the eyeball.
     */
    private Vec3d viewOrigin = new Vec3d();
    private Geometry probeTemplate;
    private Node probeRoot; 
    private ProbeContainer probes;

    private boolean probesEnabled;
    private boolean probesStarted = false;

    public MobDebugState( GameSystemManager systems ) {
        this.systems = systems;        
    }
    
    public void toggleProbesEnabled() {
        this.probesEnabled = !probesEnabled;
        resetProbesEnabled();
    }

    public void setViewOrigin( double x, double y, double z ) {
        viewOrigin.set(x, y, z);
    }
    
    public void setViewOrigin( Vec3d origin ) {
        setViewOrigin(origin.x, origin.y, origin.z);
    }
    
    public Vec3d getViewOrigin() {
        return viewOrigin;
    }    

    protected Node getRoot() {
        return ((SimpleApplication)getApplication()).getRootNode();
    }

    protected void resetProbesEnabled() {
        if( probesEnabled ) {
            if( !probesStarted ) {
                probes.start();
                getRoot().attachChild(probeRoot);
                probesStarted = true;
            }
        } else {
            if( probesStarted ) {
                probes.stop();
                probeRoot.removeFromParent();
                probesStarted = false;
            }
        }    
    }
    
    @Override
    protected void initialize( Application app ) {
        this.mobs = systems.get(MobSystem.class);
        this.stats = mobs.getStats();
        this.space = systems.get(PhysicsSpace.class);

        this.ed = systems.get(EntityData.class);
        this.probeRoot = new Node("probeRoot");        
        this.probes = new ProbeContainer(ed);
        
        Sphere mesh = new Sphere(12, 12, 1.0f);
        probeTemplate = new Geometry("probe", mesh);
        Material mat = GuiGlobals.getInstance().createMaterial(new ColorRGBA(0, 0.6f, 0f, 0.25f), true).getMaterial(); 
        mat.getAdditionalRenderState().setWireframe(true);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        probeTemplate.setMaterial(mat);
        probeTemplate.setQueueBucket(Bucket.Transparent);        

        // Setup some stats views
        DebugHudState debug = getState(DebugHudState.class);
        if( debug != null ) {
            frameTime = debug.createDebugValue("AI Time", DebugHudState.Location.Right);
            activeMobCount = debug.createDebugValue("Active Mobs", DebugHudState.Location.Right);
        }
    }
    
    @Override
    protected void cleanup( Application app ) {
        DebugHudState debug = getState(DebugHudState.class);
        if( debug != null ) {
            debug.removeDebugValue("AI Time");
            debug.removeDebugValue("Active Mobs");
        }
    }
    
    @Override
    protected void onEnable() {
        resetProbesEnabled();
    }
    
    @Override
    protected void onDisable() {
        if( probesEnabled ) {
            probes.stop();
            probeRoot.removeFromParent();
            probesStarted = false;
        }
    }
    
    public void update( float tpf ) {
        if( frameTime != null ) {
            frameTime.setObject(String.format("%.2f ms", stats.getDouble(MobStats.STAT_FRAME_TIME)/1000000.0));
            activeMobCount.setObject(String.valueOf(stats.getLong(MobStats.STAT_ACTIVE_MOB_COUNT)));
        }
        if( probesEnabled ) {
            probes.update();
            for( Probe p : probes.getArray() ) {
                p.updatePosition();
            }
        }        
    }
 
    private class Probe {        
        private Entity entity;
        private ProbeInfo info;
        private Spatial view;
        private RigidBody body;
 
        public Probe( Entity entity ) {
            this.entity = entity;
            this.info = entity.get(ProbeInfo.class);
            this.view = probeTemplate.clone();
            view.setLocalScale((float)info.getRadius());   
        }

        public void updatePosition() {
            if( body == null ) {
                this.body = space.getBinIndex().getRigidBody(entity.getId());
                if( body != null ) {
                    probeRoot.attachChild(view);
                } else {
                    return;
                }
            }
            Vec3d pos = body.localToWorld(info.getOffset(), null);
            float x = (float)(pos.x - viewOrigin.x);
            float y = (float)(pos.y - viewOrigin.y);
            float z = (float)(pos.z - viewOrigin.z);
            view.setLocalTranslation(x, y, z);   
        }        
        
        public void release() {
            view.removeFromParent();
        }       
    }
    
    private class ProbeContainer extends EntityContainer<Probe> {
        public ProbeContainer( EntityData ed ) {
            super(ed, MobType.class, ProbeInfo.class);
        }
 
        public Probe[] getArray() {
            return super.getArray();
        }
           
        protected Probe addObject( Entity e ) {
log.info("add probe for:" + e.getId());        
            Probe object = new Probe(e);
            updateObject(object, e);
            return object;
        }
        
        protected void updateObject( Probe object, Entity e ) {
        }
        
        protected void removeObject( Probe object, Entity e ) {
log.info("remove probe for:" + e.getId());        
            object.release();
        }
    }
    
}


