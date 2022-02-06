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

import java.util.*;

import infinity.es.MobType;
import infinity.es.ProbeInfo;
import org.slf4j.*;

import com.google.common.base.Function;

import com.simsilica.es.*;
import com.simsilica.mathd.*;
import com.simsilica.mathd.filter.SimpleMovingMean;
import com.simsilica.sim.*;

import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.*;

/**
 *
 *
 *  @author    Paul Speed
 */
public class MobSystem extends AbstractGameSystem {
    static Logger log = LoggerFactory.getLogger(MobSystem.class);

    private EntityData ed;
    private MPhysSystem<MBlockShape> physics;
    private PhysicsSpace<EntityId, MBlockShape> space;
    private BrainContainer brains;
    private DriverContainer drivers;
    private MobBodyInitializer initializer = new MobBodyInitializer();
 
    private BrainScheduler scheduler = new BrainScheduler();
    
    // Something simple for now... until we need things like
    // rescheduling and stuff.
    //private LinkedList<Brain> schedule = new LinkedList<>();
    
    // Queued up brains that may need rescheduling
    //private Set<Brain> reschedule = new HashSet<>();
    
    private MobStats stats = new MobStats();
    private MobStats.Stat frameTimeStat;
    private MobStats.Stat activeMobCountStat;    
    private boolean collectStats = true;

    // Just setting this up here for now
    private Map<String, MovementSettings> settingsIndex = new HashMap<>();

    public MobSystem() {
        frameTimeStat = stats.getStat(MobStats.STAT_FRAME_TIME, new SimpleMovingMean(60));
        activeMobCountStat = stats.getStat(MobStats.STAT_ACTIVE_MOB_COUNT);
    }

    // Temporary just to consolidate it
    public static String getType( AbstractBody<EntityId, MBlockShape> body ) {
        String name = body.shape.getPart().getName();
        if( "/Blocks/fence1.blocks".equals(name) ) {
            return "fence";
        }
        if( "/Blocks/fence2.blocks".equals(name) ) {
            return "fence";
        }
        if( "/Blocks/kernel.blocks".equals(name) ) {
            return "corn";
        }
        if( "/Blocks/water-trough.blocks".equals(name) ) {
            return "water";
        }
        if( name != null ) {
            return name;
        }
        log.info("getType(" + body.shape + ")");
        return "temp";   
    }

    public final MobStats getStats() {
        return stats;
    }
    
    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class, true);
        this.physics = (MPhysSystem<MBlockShape>)getSystem(MPhysSystem.class, true);

        this.space = physics.getPhysicsSpace();
        physics.getBodyFactory().addDynamicInitializer(initializer);      
 
        BrainConfigurations.initialize(ed);
        
        MovementSettings dog = new MovementSettings();
        dog.groundImpulse = 40;
        dog.movementSpeed = 2; // the base movement speed, ie: walking.
        settingsIndex.put("dog", dog);
        
        MovementSettings chicken = new MovementSettings();
        chicken.groundImpulse = 25;
        chicken.airImpulse = 25;
        settingsIndex.put("chicken", chicken);
        
        log.info("space.getContactDispatcher():" + space.getContactDispatcher());
        
        // There are two ways that a MobDriver can be set on a 
        // RigidBody.
        // 1) when the body is created on demand, if the entity is already 
        //    being managed by the 'drivers' EntityContainer then the above 
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
        // but would be easily handled is the case where we get a new MobType
        // but the body is not active... we don't specifically activate it.  We
        // assume in our simple demo that the 'just added' body hasn't had a chance
        // to fall asleep yet.
        //
        // 2021-02-13
        // I don't know for sure if the caveat above is true but I'm leaving it
        // in case I see that as a problem later.  The Mobs will go active and
        // inactive for long periods.  And it will definitely be possible for
        // mobs to pre-exist without ever having gotten a driver before.  
    }

    @Override
    protected void terminate() {
    }

    @Override
    public void start() {
        brains = new BrainContainer(ed);
        drivers = new DriverContainer(ed);
        brains.start(); 
        drivers.start(); 
    }
 
    @Override
    public void update( SimTime time ) {
    
        brains.update();
        drivers.update();

        detectEvents();
        
        if( collectStats ) {
            long start = System.nanoTime();
            scheduler.update(time);
            long end = System.nanoTime();
            frameTimeStat.updateValue(end - start);
            activeMobCountStat.updateValue(brains.size());            
        } else {
            scheduler.update(time);
        }

//        // Reschedule any pending reskeds
//        if( !reschedule.isEmpty() ) {
//            // Not efficient but functional
//            schedule.removeAll(reschedule);
//            for( Brain b : reschedule ) {
//                schedule(b);
//            }
//            reschedule.clear();
//        }  
//        
//        if( collectStats ) {
//            long start = System.nanoTime();
//            think(time);
//            long end = System.nanoTime();
//            frameTimeStat.updateValue(end - start);
//            activeMobCountStat.updateValue(brains.size());            
//        } else {
//            think(time);
//        }
    }
    
//    protected void think( SimTime time ) { 
//        if( schedule.isEmpty() ) {
//            return;
//        }                
//        // Run through all of the current 'expired' heartbeats
//        long t = time.getTime();
//        Brain brain = null;
//        while( (brain = schedule.getFirst()) != null ) {
//            if( brain.getNextHeartbeat() > t ) {
//                break;
//            }
//            // Else this should be run
//            brain.think(time);
//            schedule.removeFirst();
//                
//            // Sanity check the heartbeat
//            if( brain.getNextHeartbeat() <= t ) {
//                log.warn("possible endless loop caused by non-advancing time for:" + brain
//                            + " next heartbeat:" + brain.getNextHeartbeat() + " t:" + t);
//            }
//            schedule(brain);
//        }
//    }
    
    @Override
    public void stop() {
        brains.stop();
        brains = null;
        drivers.stop();
        drivers = null;
    }

    protected void detectEvents() {
        //log.info("detectEvents()");
        // The right solution is probably to keep track of which bins
        // that our mobs intersect with and then only check those.  Could
        // be updated when they move, etc..
        // For now, we'll brute force it and just check all active bins.
        //
        // Also, if we shuffle these into an array or set, we could conceivably
        // deal with them in chunks over several frames.  Add active bins to
        // a pending set, work them off until empty and refill.
        // Add objects to a pending set, check some number per frame against mobs
        // until empty.
        // And theoretically, those could be interleaved.
        // We don't really need frame-level accuracy for perception but we do
        // want to keep physics and AI responsive.
 
        BinIndex<EntityId, MBlockShape> binIndex = space.getBinIndex();       
        for( Bin<EntityId, MBlockShape> bin : binIndex.getActiveBins() ) {
            for( RigidBody<EntityId, MBlockShape> body : bin.getActiveObjects().getArray() ) {                
                //log.info("active body:" + body.id + "  sleepy:" + body.isSleepy());
                // Seems to nicely only be the objects that are actually active
                
                // We'll skip mobs here because mob->mob could be done in a more
                // O(n * n/2) kind of way and we may want different kinds of filtering
                // for that.
                //if( body.getControlDriver() instanceof MobDriver ) {
                //    continue;
                //}
                // The above is a nice idea but I'm letting it go for now.
                // Movement perception is inherently asymmetric.  If brain1 is moving
                // and brain2 is not then only one gets the notification.  Or if brain1
                // cannot see brain2 because perception checks, etc..  The brain->brain
                // loop is not so simple.
                
                // Is it really moving, though
                double vSq = body.getLinearVelocity().lengthSq();
                // We'll limit all perception to 1 mm/sec.  Particular actors may
                // further limit that if they just don't notice details.
                double minVelocity = 0.001; 
                if( vSq < minVelocity * minVelocity ) {
                    continue;
                } 
 
                // We'll base things purely on bounding sphere for now... even
                // when we don't that would be our broadphase check anyway
                double radius = body.shape.getMass().getRadius();
 
                // Brute-force, no special spatial indexes.  FIXME: use a bin system or something               
                for( Brain brain : brains.getArray() ) {
                
                    // Don't deliver our own events
                    if( brain.getId().getId() == body.id.getId() ) {
                        continue;
                    } 
                
                    // Really need to define our own sphere primitive
                    Vec3d pos = brain.getActor().getPosition();
                    double perc = 2; // just hard-code something for now... should be the same
                                     // as the distance in Actor.look(), though.
                                     // The fact that we have two different places in the
                                     // code is a problem.  FIXME: consolidate perception checks
 
                    // Everything at the moment is a chicken and we'll limit
                    // chickens to movement of 5 cm/sec or more
                    if( vSq < 0.05 * 0.05 ) {
                        continue;
                    }
                    // Calculating perception out here means we don't
                    // create lots of SeenObjects that will never actually be
                    // seen.  It also makes the AI code simpler in general.
                    // However, there is a good argument to be made that the 'brain'
                    // would already have the information to filter this out and
                    // might want to do something even more complicated.
                    // I think in the end, doing some broad perception checks out here
                    // is best.
                       
                    double d = body.position.distanceSq(pos);
                    double thresh = radius + perc;                    
                    if( d < thresh * thresh ) {
                        //log.info("Can see movement:" + body.id);
                        SeenObject seen = new SeenObject(body.id, body.position, 
                                                         body.orientation, body.getLinearVelocity(),
                                                         body.shape, getType(body), Math.sqrt(d));
                         
                        if( brain.objectMoved(seen) ) {
                            //reschedule.add(brain);
                            scheduler.reschedule(brain);
                        }
                    } 
                }
            }
        }
    }

//    protected void schedule( Brain brain ) {
//        if( schedule.isEmpty() ) {
//            schedule.add(brain);
//            return;
//        }
//        
//        long search = brain.getNextHeartbeat();
//        for( ListIterator<Brain> it = schedule.listIterator(0); it.hasNext(); ) {
//            Brain item = it.next();
//            // Just in case, abort if we find ourselves
//            if( item == brain ) {
//                return;
//            }
//            if( item.getNextHeartbeat() > search ) {
//                // Need to be before this item so back up one
//                it.previous();
//                it.add(brain);
//                return;
//            }
//        }
//        // Made it all the way through the list without something that
//        // should be run after us... so just add it to the end
//        schedule.add(brain); 
//    }

    // Even though they are largely doing parallel entity processing,
    // I'm keeping the bain container and the mob driver container separate
    // because 'brains' are core parts of the AI while the drivers are specif
    // to physics integration and should be swappable at some point, ie:
    // a totally separate system.

    private class BrainContainer extends EntityContainer<Brain> {
        public BrainContainer( EntityData ed ) {
            super(ed, MobType.class);
        }
        
        public Brain[] getArray() {
            return super.getArray();
        }
        
        @Override          
        protected Brain addObject( Entity e ) {

            String type = e.get(MobType.class).getTypeName(ed);
            BrainConfiguration config = BrainConfigurations.getConfig(type);
            Brain result = new Brain(ed, e.getId(), config);

            // See if it already has a driver
            MobDriver driver = drivers.getObject(e.getId());
            if( driver != null ) {
                log.info("Setting existing actor to body for:" + e.getId());
                result.setActor(driver);
                driver.setBrain(result);
                scheduler.add(result);
            }
                    
            return result;
        }
    
        @Override          
        protected void updateObject( Brain driver, Entity e ) {
        }
    
        @Override          
        protected void removeObject( Brain driver, Entity e ) {
log.info("removeObject(" + e + ")");
        }        
    }     

    private class DriverContainer extends EntityContainer<MobDriver> {
     
        public DriverContainer( EntityData ed ) {
            super(ed, MobType.class);
        }
 
        @Override          
        protected MobDriver addObject( Entity e ) {
            MobDriver result = new MobDriver(physics, e.getId());
 
            String type = e.get(MobType.class).getTypeName(ed);
            MovementSettings ms = settingsIndex.get(type);
log.info("type:" + type + "  settings:" + ms);            
            if( ms != null ) {
                result.setMovementSettings(ms);
            } 

            // If there is a probe then pass it on... for now at
            // least, probes cannot be reset at runtime.  We'd either have
            // to require them or add another container.  Wait for a use-case.
            // (This is also what we'd do if we needed non-sphere shapes, etc.)
            ProbeInfo probe = ed.getComponent(e.getId(), ProbeInfo.class);
            if( probe != null ) {
                result.setProbeInfo(probe);
            }
                
            // See if the physics engine already has a body for this entity
            RigidBody<EntityId, MBlockShape> body = space.getBinIndex().getRigidBody(e.getId());
log.info("existing body:" + body);
            if( body != null ) {
                body.setControlDriver(result);
            }
            
            // See if there is already a brain
            Brain brain = brains.getObject(e.getId());
            if( brain != null ) {
                log.info("Setting created actor on existing brain for:" + e.getId());
                brain.setActor(result);
                result.setBrain(brain);
                scheduler.add(brain);
            }            
                    
            return result;
        }
    
        @Override          
        protected void updateObject( MobDriver driver, Entity e ) {
        }
    
        @Override          
        protected void removeObject( MobDriver driver, Entity e ) {
log.info("removeObject(" + e + ")");
            driver.release();
        }        
    }
    
    // I guess this could have been done with an ObjectStatusListener instead
    private class MobBodyInitializer implements Function<RigidBody<EntityId, MBlockShape>, Void> {
        public Void apply( RigidBody<EntityId, MBlockShape> body ) {
            // See if this is one of the ones we need to add a player driver to
            MobDriver driver = drivers.getObject(body.id);
log.info("MobBodyInitializer.apply(" + body + ")  driver:" + driver);        
            if( driver != null ) {
                body.setControlDriver(driver);
            }
            return null;
        }
    }    
}

