/*
 * $Id$
 * 
 * Copyright (c) 2020, Simsilica, LLC
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

package infinity.sim;

import org.slf4j.*;

import infinity.es.input.CharacterInput;
import infinity.es.input.MovementInput;

import com.google.common.base.Function;

import com.simsilica.es.*;
import com.simsilica.mathd.*;
import com.simsilica.sim.*;

import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.UprightDriver;
import com.simsilica.mphys.RigidBody;

/**
 *  Manages the control drivers of entities with MovementInput
 *  components and makes sure that have the latest movement input
 *  data.
 *
 *  @author    Paul Speed
 */
public class MovementSystem extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(MovementSystem.class);

    private EntityData ed;
    private MPhysSystem<MBlockShape> physics;    
    private PlayerContainer players;
    private MobContainer mobs;
    private MovementBodyInitializer initializer = new MovementBodyInitializer();
    private PhysicsSpace<EntityId, MBlockShape> space;
                
    public MovementSystem() {
    }
 
    // Temporary for testing
    public PlayerDriver getDriver( EntityId id ) {
        return players.getObject(id); 
    } 
     
    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        if( ed == null ) {
            throw new RuntimeException(getClass().getName() + " system requires an EntityData object.");
        }
        this.physics = (MPhysSystem<MBlockShape>)getSystem(MPhysSystem.class);
        if( physics == null ) {
            throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
        }

        this.space = physics.getPhysicsSpace();
        physics.getBodyFactory().addDynamicInitializer(initializer);      
        
        // There are two ways that a PlayerDriver can be set on a 
        // RigidBody.
        // 1) when the body is created on demand, if the entity is already 
        //    being managed by the 'players' EntityContainer then the above 
        //    initialize will just set the existing driver.
        // 2) if the rigid body already exists when the entity as added
        //    to the 'players' EntityContainer then the created driver is set
        //    on the body then.     
        //
        // This covers all use-cases... bin becoming active before the player
        // container saw the entity, entity having its MovementInput removed/added
        // on the fly, body's bin going to sleep and getting activated again, etc..
        // All possible life cycle paths are handled by this two-prong approach.
        // ...and probably a few I haven't thought of.  (One case we don't handle
        // but would be easily handled is the case where we get a new MovementInput
        // but the body is not active... we don't specifically activate it.  We
        // assume in our simple demo that the 'just added' body hasn't had a chance
        // to fall asleep yet.
        //
        // This control driver handling is trickier than in some of the simpler ES 
        // demos or the SiO2 bullet-char demo because the rigid bodies can be dynamically 
        // loaded and unloaded in mphys.      
    }
    
    @Override
    protected void terminate() {
    }

    @Override
    public void start() {
        players = new PlayerContainer(ed);
        players.start(); 
        mobs = new MobContainer(ed);
        mobs.start(); 
    }
 
    @Override
    public void update( SimTime time ) {
        players.update();
        mobs.update();
    }
    
    @Override
    public void stop() {
        players.stop();
        players = null;
        mobs.stop();
        mobs = null;
    }
    
    private class PlayerContainer extends EntityContainer<PlayerDriver> {
     
        public PlayerContainer( EntityData ed ) {
            super(ed, MovementInput.class);
        }

        public PlayerDriver[] getArray() {
            return super.getArray();
        }
 
        @Override          
        protected PlayerDriver addObject( Entity e ) {
log.info("addObject(" + e + ")");        

            // For now we'll just hack in a sword here.  Bad but
            // convenient.
            //EntityId sword = getSystem(GameEntities.class).createSword(e.getId(), new Vec3d(0, 0, 1), new Quatd()); 
            //EntityId sword = getSystem(GameEntities.class).createOpenBox(e.getId(), new Vec3d(0, 0, 1), new Quatd()); 

            PlayerDriver result = new PlayerDriver(e.getId(), ed, null);
            
            // See if the physics engine already has a body for this entity
            RigidBody<EntityId, MBlockShape> body = space.getBinIndex().getRigidBody(e.getId());
log.info("existing body:" + body);
            if( body != null ) {
                body.setControlDriver(result);
            }            
                    
            return result;
        }
    
        @Override          
        protected void updateObject( PlayerDriver driver, Entity e ) {
            if( log.isTraceEnabled() ) {
                log.trace("updateObject(" + e + ")");
            }        
            MovementInput ms = e.get(MovementInput.class);
            driver.applyMovementInput(ms);
        }
    
        @Override          
        protected void removeObject( PlayerDriver driver, Entity e ) {
log.info("removeObject(" + e + ")");        
            //physics.setControlDriver(e.getId(), null);
        }        
    }

    private class MobContainer extends EntityContainer<UprightDriver> {
     
        public MobContainer( EntityData ed ) {
            super(ed, CharacterInput.class);
        }

        public UprightDriver[] getArray() {
            return super.getArray();
        }
 
        @Override          
        protected UprightDriver addObject( Entity e ) {

            UprightDriver result = new UprightDriver();
            
            // See if the physics engine already has a body for this entity
            RigidBody<EntityId, MBlockShape> body = space.getBinIndex().getRigidBody(e.getId());
log.info("existing body:" + body);
            if( body != null ) {
                body.setControlDriver(result);
            }            
                    
            return result;
        }
    
        @Override          
        protected void updateObject( UprightDriver driver, Entity e ) {
            if( log.isTraceEnabled() ) {
                log.trace("updateObject(" + e + ")");
            }        
            CharacterInput ms = e.get(CharacterInput.class);
        }
    
        @Override          
        protected void removeObject( UprightDriver driver, Entity e ) {
log.info("removeObject(" + e + ")");        
            //physics.setControlDriver(e.getId(), null);
        }        
    }
 
    // I guess this could have been done with an ObjectStatusListener instead
    private class MovementBodyInitializer implements Function<RigidBody<EntityId, MBlockShape>, Void> {
        public Void apply( RigidBody<EntityId, MBlockShape> body ) {
            // See if this is one of the ones we need to add a player driver to
            PlayerDriver driver = players.getObject(body.id);
log.info("MovementBodyInitializer.apply(" + body + ")  driver:" + driver);        
            if( driver != null ) {
                body.setControlDriver(driver);
            }
            
            // or a character driver
            UprightDriver charDriver = mobs.getObject(body.id);
            if( charDriver != null ) body.setControlDriver(charDriver);
                        
            return null;
        }
    }    
}    


