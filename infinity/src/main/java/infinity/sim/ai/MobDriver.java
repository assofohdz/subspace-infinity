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

package com.simsilica.demo.sim.ai;

import java.util.*;

import org.slf4j.*;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import com.simsilica.es.*;
import com.simsilica.es.common.*;
import com.simsilica.mathd.*;
import com.simsilica.mblock.phys.*;
import com.simsilica.mphys.*;

import com.simsilica.crig.*;
import com.simsilica.ext.mphys.*;

import com.simsilica.demo.es.*;

/**
 *  When an AI Mob is a phyics rigid body, it will be controlled
 *  by a MobDriver.  This converts more general input from the
 *  behavior system into rigid body forces.
 *
 *  @author    Paul Speed
 */
public class MobDriver extends AbstractControlDriver<EntityId, MBlockShape> implements Actor {
    static Logger log = LoggerFactory.getLogger(MobDriver.class);

    private static final double TWO_PI = Math.PI * 2;

    private MPhysSystem<MBlockShape> physics;
    private EntityData ed;
    private EntityId mob;
    private Brain brain;

    // Temp storage
    private double[] angles = new double[3];
    private Vec3d force = new Vec3d();

    // Input controlled parameters
    private Vec3d desiredVelocity = new Vec3d();
    private Quatd orientation = new Quatd();
    private double facing;
    private double targetFacing;
    private Vec3d move = new Vec3d();

    private Vec3d lastPosition = new Vec3d();
    private Vec3d actualVelocity = new Vec3d();
    private Vec3d averageVelocity = new Vec3d();

    private MovementSettings settings = new MovementSettings();

    // Should be configurable per mob type
    //private double groundImpulse = 50;
    //private double airImpulse;
    //private double turnSpeed = TWO_PI; // 360 degrees per second

    // A simplified version of something that should be its own type + strategys
    private double perceptionRadius = 2;

    // A probe we will use to query the world to see if we are about
    // to bump into something and then potentially steer a bit side
    // to side.
    private ProbeInfo probeInfo;
    private Probe probe;
    //private MBlockShape probeShape;
    //private Vec3d probeOffset;
    //private Quatd probeOrientation;
    //private QueryFilter probeFilter = new QueryFilter(QueryFilter.TYPE_ALL);

    private RigShape rigShape;
    private AnimPump animPump;

    public MobDriver( MPhysSystem<MBlockShape> physics, EntityId mob ) {
        this.physics = physics;
        this.ed = physics.getEntityData();
        this.mob = mob;
    }

    @Override
    public void initialize( RigidBody<EntityId, MBlockShape> body ) {
        super.initialize(body);
        log.info("initialize(" + body + ")");
        if( body.shape instanceof RigShape ) {
            this.rigShape = (RigShape)body.shape;

            // Always at least a null layer
            this.animPump = new AnimPump(rigShape, null);
            animPump.setCurrentAction("Idle", 1);
            rigShape.update();

            log.info("rig shape:" + rigShape);
            //dumpShape(rigShape);
            //rigShape.setLayerAction(null, "Idle");
            //rigShape.setTime(null, 1);
            //rigShape.update();
            //dumpShape(rigShape);
        }
    }

    protected void dumpShape( MBlockShape shape ) {
        log.info("dumpShape(" + shape + ")");
        Part root = shape.getPart();
        if( root instanceof Group ) {
            for( Part child : ((Group)root).getChildren() ) {
                log.info("   " + child.getName() + ":" + child.getShapeRelativePosition());
            }
        } else {
            log.info(" root:" + root.getName() + ":" + root.getShapeRelativePosition());
        }
    }

    public void setProbeInfo( ProbeInfo probeInfo ) {
        if( this.probeInfo == probeInfo ) {
            return;
        }
        this.probeInfo = probeInfo;
        if( probeInfo == null ) {
            this.probe = null;
        } else {
            this.probe = new Probe(probeInfo);
        }

        // For now, hard-coded for a chicken that is 0.35 radius
        //probe = new Probe();
        //probeShape = MBlockShape.createGhost(0.3);
        //probeOffset = new Vec3d(0, 0.1, 0.3); //0.56);
        //probeOrientation = new Quatd();
    }

    // This makes me a little uncomfortable to have this mutually
    // dependent relationship.  Probably there is a listener missing
    // but this is convenient for event callbacks.
    public void setBrain( Brain brain ) {
        this.brain = brain;
    }

    public void setMovementSettings( MovementSettings settings ) {
        this.settings = settings;
    }

    public MovementSettings getMovementSettings() {
        return settings;
    }

    // From the actor interface
    @Override
    public Vec3d getPosition() {
        return getBody().position;
    }

    @Override
    public double getFacing() {
        return facing;
    }

    @Override
    public void turnTo( double facing ) {
        this.targetFacing = facing;
    }

    @Override
    public void move( Vec3d move ) {
        this.move.set(move).multLocal(settings.movementSpeed);
    }

    @Override
    public Iterable<SeenObject> search() {
        return search(Predicates.alwaysTrue());
    }

    @Override
    public Iterable<SeenObject> search( String... types ) {
        return search(Arrays.asList(types));
    }

    @Override
    public Iterable<SeenObject> search( Collection<String> types ) {
        return search(Predicates.in(types));
    }

    public Iterable<SeenObject> search( Predicate<? super String> filter ) {

        // For now the inefficient way  FIXME: use positional grid searches and
        // split queries for static/dynamic
        //
        // Actually, search of live objects should probably be a physics
        // query using some shape like a cone or sphere.

        // Eventually we will want to know look direction, etc.
        // For now just use a simple radius check
        double radius = perceptionRadius; // chickens are near-sighted in this demo

        // Could be a few cases here:
        // 1) rigid body (because radius is going to be relatively small, it should
        //    be active if we care about it)
        // 2) static body (same caveat as above)
        // 3) entity with spawn position and shape but no mass, ie: not managed by physics
        // 4) entity with only spawn position.

        // For now we will handle (1) and (2) because they are straight-forward
        // and eventually we'll want to move that query to the physics space/collision
        // system most likely.

        List<SeenObject> results = new ArrayList<>();
        for( EntityId id : ed.findEntities(null, SpawnPosition.class, ShapeInfo.class, Mass.class) ) {
            if( id == mob ) {
                // We don't see ourselves
                continue;
            }
            RigidBody<EntityId, MBlockShape> rb = physics.getPhysicsSpace().getBinIndex().getRigidBody(id);
            if( rb != null ) {
                String type = MobSystem.getType(rb);
                if( !filter.apply(type) ) {
                    continue;
                }
                // for now, dumb distance that ignores object size
                double dist = rb.position.distance(getBody().position);
                if( dist <= radius ) {
                    results.add(new SeenObject(id, rb.position, rb.orientation, rb.getLinearVelocity(),
                                               rb.shape, type, dist));
                    continue;
                }
            }

            StaticBody<EntityId, MBlockShape> sb = physics.getPhysicsSpace().getBinIndex().getStaticBody(id);
            if( sb != null ) {
                String type = MobSystem.getType(sb);
                if( !filter.apply(type) ) {
                    continue;
                }
                double dist = sb.position.distance(getBody().position);
                if( dist <= radius ) {
                    results.add(new SeenObject(id, sb.position, sb.orientation, Vec3d.ZERO,
                                               sb.shape, type, dist));
                    continue;
                }
            }
        }

        return results;
    }

    @Override
    public SeenObject look( EntityId id ) {
        // Same cases as above and we'll still only deal with (1) and (2) for
        // the moment.
        double radius = perceptionRadius; // chickens are near-sighted in this demo
        RigidBody<EntityId, MBlockShape> rb = physics.getPhysicsSpace().getBinIndex().getRigidBody(id);
        if( rb != null ) {
            // for now, dumb distance that ignores object size
            // Well, we don't ignore it completeley anymore but it is just
            // a gross approximation
            double size = rb.shape.getMass().getRadius();
            double dist = rb.position.distance(getBody().position);
            if( dist <= (radius + size) ) {
                return new SeenObject(id, rb.position, rb.orientation, rb.getLinearVelocity(),
                                      rb.shape, MobSystem.getType(rb), dist);
            }
        }

        StaticBody<EntityId, MBlockShape> sb = physics.getPhysicsSpace().getBinIndex().getStaticBody(id);
        if( sb != null ) {
            double dist = sb.position.distance(getBody().position);
            if( dist <= radius ) {
                return new SeenObject(id, sb.position, sb.orientation, Vec3d.ZERO,
                                      sb.shape, MobSystem.getType(sb), dist);
            }
        }
        return null;
    }

    @Override
    public SeenObject look2( EntityId id ) {
        // Same cases as above and we'll still only deal with (1) and (2) for
        // the moment.
        double radius = perceptionRadius; // chickens are near-sighted in this demo
        RigidBody<EntityId, MBlockShape> rb = physics.getPhysicsSpace().getBinIndex().getRigidBody(id);
        if( rb != null ) {
            // Well, we don't ignore it completeley anymore but it is just
            // a gross approximation
            double size = rb.shape.getMass().getRadius();
            double dist = rb.position.distance(getBody().position);
log.info("look2(" + id + ") distance:" + dist);
            if( dist <= (radius + size) ) {
                return new SeenObject(id, rb.position, rb.orientation, rb.getLinearVelocity(),
                                      rb.shape, MobSystem.getType(rb), dist);
            }
        } else {
log.info("look2() no body");
        }

        StaticBody<EntityId, MBlockShape> sb = physics.getPhysicsSpace().getBinIndex().getStaticBody(id);
        if( sb != null ) {
            double dist = sb.position.distance(getBody().position);
            if( dist <= radius ) {
                return new SeenObject(id, sb.position, sb.orientation, Vec3d.ZERO,
                                      sb.shape, MobSystem.getType(sb), dist);
            }
        }
        return null;
    }

    @Override
    public void say( long startTime, long endTime, String text ) {
        EntityId entity = ed.createEntity();
        ed.setComponents(entity, new Speech(mob, text), new Decay(startTime, endTime));
    }

    public void release() {
        // If we still have a body and we are still the driver
        // for it, then clear ourselves
        if( getBody() != null && getBody().getControlDriver() == this ) {
            getBody().setControlDriver(null);
            // The callback to terminate will clear our body reference
        }
    }

    protected void killVerticalRotation( RigidBody<EntityId, MBlockShape> body ) {

        // Kill any non-yaw orientation
        body.orientation.toAngles(angles);
        if( angles[0] != 0 || angles[2] != 0 ) {
            angles[0] = 0;
            angles[2] = 0;
            body.orientation.fromAngles(angles);
        }

        // Kill any non-yaw velocity
        Vec3d rot = body.getRotationalVelocity();
        if( rot.x != 0 || rot.z != 0 ) {
            rot.x = 0;
            //rot.y *= 0.95; // Let's see if we can dampen the spinning
            rot.z = 0;

            // Don't really need to set it back but just in case
            body.setRotationalVelocity(rot);
        }
        // The above is copied from UprightDriver and is probably only
        // temporary in its current form.  There may end up being cases
        // where we calculate what "up" is for a mob differently.
    }

    @Override
    public void update( long frameTime, double step ) {
        RigidBody<EntityId, MBlockShape> body = getBody();
        if( log.isTraceEnabled() ) {
            log.trace("update(" + step + ")  temperature:" + body.getTemperature());
        }

        killVerticalRotation(body);

        // Here we could early out when we get a handle on what 'no input' means

        if( facing != targetFacing ) {
            // Need to deal with the cases where facing is like 5 degrees
            // and targetFacing is 355 degrees.  Need to know to just turn
            // 10 degrees instead of going all the way around.
            if( facing > targetFacing && facing - targetFacing < Math.PI ) {
                // Better to turn negative... covers the simple case where
                // facing and target are in the same domain and facing is more than
                // target
                facing = Math.max(targetFacing, facing - step * settings.turnSpeed);
            } else if( targetFacing > facing && targetFacing - facing < Math.PI ) {
                // Better to turn positive... covers the simple case where
                // facing and target are in the same domain and facing is less than
                // target
                facing = Math.min(targetFacing, facing + step * settings.turnSpeed);
            } else {
                // We must have wrapped around 0
                if( facing > targetFacing ) {
                    double t = targetFacing + TWO_PI;
                    facing = Math.min(t, facing + step * settings.turnSpeed);
                } else {
                    double f = facing + TWO_PI;
                    facing = Math.max(targetFacing, f - step * settings.turnSpeed);
                }
            }
            if( facing < 0 ) {
                facing += TWO_PI;
            } else if( facing > TWO_PI ) {
                facing -= TWO_PI;
            }
//log.info("facing:" + facing);
            orientation.fromAngles(0, facing, 0);
        } else if( probe != null ) {
            // If we are already heading in our intended direction then
            // see if there is anything in the way.  Trying to strike a balance
            // between making sure the mob heads the way the AI is telling it
            // while also elastically walking around simple obstacles.
            //Vec3d v = body.localToWorld(probeOffset, null);
            probe.reset();
            physics.getPhysicsSpace().queryContacts(probe.position, probe.orientation, probe.shape, probe.filter, probe);
//log.info("body pos:" + body.position + "  prob pos:" + probe.position);
            if( probe.closest != null ) {
                //log.info("closest:" + probe.closest);
                //v.set(probe.closest.contactPoint).subtractLocal(body.position);
                //Vec3d dir = body.orientation.mult(Vec3d.UNIT_Z);
                //Vec3d left = body.orientation.mult(Vec3d.UNIT_X);
                double turn = probe.turn;//left.dot(v);
                double fwd = probe.forward;//dir.dot(v);
                //log.info("******* turn:" + turn + "  fwd:" + fwd); // + "   offset:" + v + "   left:" + left);

                // When left is positive, we want to turn right and when
                // left is negative we want to turn left... but I'm pretty sure
                // the x,z plane is backwards from what one might think.
                double delta = 0.05; //0.2;
                if( turn < 0 ) {
                    targetFacing += delta;
                    //facing = targetFacing;
                    //orientation.fromAngles(0, facing, 0);
                } else if( turn > 0 ) {
                    targetFacing -= delta;
                    //facing = targetFacing;
                    //orientation.fromAngles(0, facing, 0);
                }
            }
        }

        orientation.mult(move, desiredVelocity);

        if( desiredVelocity.lengthSq() > 0 ) {

            // Calculate how much our velocity has to change to reach
            // the desired velocity
            force.set(desiredVelocity).subtractLocal(body.getLinearVelocity());

            if( desiredVelocity.y < 0.0001 ) {
                // Don't kill our gravity unless we are using vertical thrust
                force.y = 0;
            }

//Vec3d v1 = new Vec3d(desiredVelocity.x, 0, desiredVelocity.z);
////Vec3d v2 = new Vec3d(body.getLinearVelocity().x, 0, body.getLinearVelocity().z);
////Vec3d v3 = body.position.subtract(lastPosition);  v3.y = 0;
////log.info("desired:" + v1.length() + "  body:" + v2.length() + "  actual:" + (v3.length()/step));
////log.info("desired:" + v1.length() + "  body:" + v2.length() + "  actual:" + (v3.length()));
//log.info("desired:" + v1.length() + "  actual:" + actualVelocity.length() + "  average:" + averageVelocity.length());

            // We could kill vertical velocity here based on contacts, in water, etc.

            // Right now, we'll treat everything as ground contact
            force.multLocal(settings.groundImpulse * (1.0/body.getInverseMass()));

            //body.addForce(force);
            body.addForceAtPoint(force, 0.1, body.position);

            // If our average velocity is low and we have pushback in the direction
            // we want to go then send and event to the brain and let it figure out
            // what to do.
            // Questionable but we filter out low values of max push back to avoid
            // auto-blocking when a mob is moving and already in glancing contact
            // with something.
            if( maxPushback > 0.2 && averageVelocity.lengthSq() < (0.001 * 0.001)) {
                //log.info("blocked by:" + mostBlocked);
                log.info("***   max pushback:" + maxPushback);
                brain.blocked(mostBlocked.contactNormal);
            }
        }

        // Always enforce orientation, I guess.  Otherwise the mobs will just
        // drift around.  Probably we want some middle ground.
        // ...probably we want to do this but base the facing -> targetFacing
        // interpolation on the actual current facing.  So we'll extrapolate
        // 'facing' from the current transform and then try to correct. That
        // would let mobs get thrown around while still supporting keeping them
        // from drifting when no input is being supplied.
        body.orientation.set(orientation);

        // Adding a force will only wake the object up if it's sleepy...
        // we want to force our player objects awake since they will be constantly
        // updated.  Note that things handle sleeping perfectly fine if we weren't
        // trying to apply forces every frame.  The driver will be added/removed, etc..
        // So it would be possible to have even player controlled objects that sleep
        // when the player isn't providing input.  We just don't do that.
        body.wakeUp(true);

        // When we have a real avatar, here is where we will want to rotate
        // the body to match orientation... or move towards it or whatever.
        // The truth is complicated as we may opt for head-turn = looking
        // and the body only turns to face direction of movement relative to
        // head or something.  And it could be specific to mob type.

        // Keep track of the 'real' velocity of the object
        actualVelocity.set(body.position).subtractLocal(lastPosition);
        averageVelocity.addLocal(actualVelocity).multLocal(0.5);
        lastPosition.set(body.position);

        mostBlocked = null;
        maxPushback = 0;

        if( animPump != null && step > 0 ) {
            // See which animation we should be using.
            double speed = averageVelocity.length() / step;
            String action = "Idle";
            double animSpeed = 1.0;
            if( Math.abs(speed) > 0.001 ) {
                action = "Walk";
                //animSpeed = (speed / 1.5) * 1.75;
                animSpeed = (speed / 1.5) * 1.8;
            } else {
                log.info("actual velocity:" + actualVelocity + "  averageVelocity:" + averageVelocity);
            }
//log.info("setCurrentAction(" + action + ", " + animSpeed + ") speed:" + speed);
            animPump.setCurrentAction(action, animSpeed);

            animPump.update(step);
            rigShape.update();
        }
    }

    private Contact mostBlocked = null;
    private double maxPushback = 0;

    @Override
    public void newContact( Contact<EntityId, MBlockShape> contact ) {
        //if( contact.body2 == null ) {
        //    log.info("newContact(world) normal:" + contact.contactNormal);
        //} else {
        //    log.info("newContact(" + contact + ")");
        //}

        double push = -contact.contactNormal.dot(desiredVelocity);
        if( push > maxPushback ) {
            mostBlocked = contact;
            maxPushback = push;
        }

        if( contact.getBody2() == null ) {
            // We don't do anything with world contacts yet... but we will
            // eventually need them for auto-climbing, 'on ground' dection, etc.
            return;
        }

        RigidBody<EntityId, MBlockShape> rb = contact.body1;
        if( rb == getBody() ) {
            rb = contact.getBody2();
        }
        String type = MobSystem.getType(rb);
        if( !brain.isInterestingTouch(type) ) {
            return;
        }

        SeenObject object = new SeenObject(rb.id, rb.position, rb.orientation, rb.getLinearVelocity(),
                                           rb.shape, type, contact.penetration);
        brain.touch(new TouchEvent(object, contact.contactPoint, contact.contactNormal));
    }

    private class Probe implements ContactListener<EntityId, MBlockShape> {

        private MBlockShape shape;
        private Vec3d offset;
        private Quatd orientation = new Quatd(); // mostly because we don't have an identity constant.
        private QueryFilter filter = new QueryFilter(QueryFilter.TYPE_ALL);

        private Vec3d position = new Vec3d();
        private double minDistanceSq;
        private Contact<EntityId, MBlockShape> closest;
        private Vec3d relative = new Vec3d();
        private Vec3d dir = new Vec3d();
        private Vec3d left = new Vec3d();
        private double turn;
        private double forward;

        public Probe( ProbeInfo info ) {
            this.shape = MBlockShape.createGhost(info.getRadius());
            this.offset = info.getOffset();
        }

        public void reset() {
            getBody().localToWorld(offset, position);
            this.minDistanceSq = Double.POSITIVE_INFINITY;
            this.closest = null;
            getBody().orientation.mult(Vec3d.UNIT_Z, dir);
            getBody().orientation.mult(Vec3d.UNIT_X, left);
            this.turn = 0;
        }

        public void newContact( Contact<EntityId, MBlockShape> contact ) {
            // See if it's a contact that we're even interested in

            if( contact.body1 == getBody() || contact.body2 == getBody() ) {
                // Self contact
                return;
            }

            relative.set(contact.contactPoint).subtractLocal(getBody().position);

            //double facing = contact.contactNormal.dot(dir);
            // 'facing' or not depends on relative position and not our
            // mob's facing dir.
            double facing = contact.contactNormal.dot(relative);
            if( facing > 0 ) {
                // If facing is positive then that means the contact normal
                // points roughly in the same direction that we're facing
                // so it's the back side of something (from our perspective).
                //log.info("Skipping back-facing contact:" + facing + "  " + contact);
                return;
            }

            double fwd = relative.dot(dir);
            //if( fwd < 0.37 ) {
                // "Too close" is going to take some more tweaking and we
                // probably want to consider 'leftness', too.
                // Could be that if we add an oscillation test then we could
                // let more contacts through anyway.
            //    log.info("Too close:" + fwd + "  " + contact);
            //    return;
            //}

            double distSq = contact.contactPoint.distanceSq(getBody().position);
            if( distSq < minDistanceSq ) {
                closest = contact;
                minDistanceSq = distSq;
                turn = relative.dot(left);
                forward = fwd;
            }
        }
    }
}

