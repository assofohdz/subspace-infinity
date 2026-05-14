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

package infinity.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.simsilica.crig.RigShape;
import infinity.es.ProbeInfo;
import infinity.es.Speech;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.common.Decay;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.Group;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mblock.phys.Part;
import com.simsilica.mphys.AbstractControlDriver;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.QueryFilter;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.StaticBody;

import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;

/**
 * When an AI Mob is a phyics rigid body, it will be controlled by a MobDriver. This converts more
 * general input from the behavior system into rigid body forces.
 *
 * @author Paul Speed
 */
// Identity-by-design `==`: physics RigidBody references are unique-per-body
// in dyn4j; getControlDriver()==this is the canonical "is THIS body's driver"
// check; setter no-op short-circuit on identity. None of the flagged sites
// are value-equality bugs.
@SuppressWarnings("PMD.CompareObjectsWithEquals")
public class MobDriver extends AbstractControlDriver<EntityId, MBlockShape> implements Actor {
  private static final double TWO_PI = Math.PI * 2;
  static Logger log = LoggerFactory.getLogger(MobDriver.class);
  private final MPhysSystem<MBlockShape> physics;
  private final EntityData ed;
  private final EntityId mob;
  private Brain brain;

  // Temp storage
  private final double[] angles = new double[3];
  private final Vec3d force = new Vec3d();

  // Input controlled parameters
  private final Vec3d desiredVelocity = new Vec3d();
  private final Quatd orientation = new Quatd();
  private double facing;
  private double targetFacing;
  private final Vec3d move = new Vec3d();

  private final Vec3d lastPosition = new Vec3d();
  private final Vec3d actualVelocity = new Vec3d();
  private final Vec3d averageVelocity = new Vec3d();

  private MovementSettings settings = new MovementSettings();

  // A simplified version of something that should be its own type + strategys
  private static final double PERCEPTION_RADIUS = 2;

  // A probe we will use to query the world to see if we are about
  // to bump into something and then potentially steer a bit side
  // to side.
  private ProbeInfo probeInfo;
  private Probe probe;

  private RigShape rigShape;
  private AnimPump animPump;
  private Contact mostBlocked = null;
  private double maxPushback = 0;

  public MobDriver(final MPhysSystem<MBlockShape> physics, final EntityId mob) {
    this.physics = physics;
    this.ed = physics.getEntityData();
    this.mob = mob;
  }

  @Override
  public void initialize(final RigidBody<EntityId, MBlockShape> body) {
    super.initialize(body);
    log.info("initialize({})", body);
    if (body.shape instanceof RigShape) {
      this.rigShape = (RigShape) body.shape;

      // Always at least a null layer
      this.animPump = new AnimPump(rigShape, null);
      animPump.setCurrentAction("Idle", 1);
      rigShape.update();

      log.info("rig shape:{}", rigShape);
    }
  }

  protected void dumpShape(final MBlockShape shape) {
    if (!log.isInfoEnabled()) {
      return;
    }
    log.info("dumpShape({})", shape);
    Part root = shape.getPart();
    if (root instanceof Group) {
      for (final Part child : ((Group) root).getChildren()) {
        if (log.isInfoEnabled()) {
          log.info("   {}:{}", child.getName(), child.getShapeRelativePosition());
        }
      }
    } else {
      if (log.isInfoEnabled()) {
        log.info(" root:{}:{}", root.getName(), root.getShapeRelativePosition());
      }
    }
  }

  public void setProbeInfo(final ProbeInfo probeInfo) {
    if (this.probeInfo == probeInfo) {
      return;
    }
    this.probeInfo = probeInfo;
    if (probeInfo == null) {
      this.probe = null;
    } else {
      this.probe = new Probe(probeInfo);
    }
  }

  // This makes me a little uncomfortable to have this mutually
  // dependent relationship.  Probably there is a listener missing
  // but this is convenient for event callbacks.
  public void setBrain(final Brain brain) {
    this.brain = brain;
  }

  public MovementSettings getMovementSettings() {
    return settings;
  }

  public void setMovementSettings(final MovementSettings settings) {
    this.settings = settings;
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
  public void turnTo(final double facing) {
    this.targetFacing = facing;
  }

  @Override
  public void move(final Vec3d move) {
    this.move.set(move).multLocal(settings.getMovementSpeed());
  }

  @Override
  public Iterable<SeenObject> search() {
    return search(Predicates.alwaysTrue());
  }

  @Override
  public Iterable<SeenObject> search(final String... types) {
    return search(Arrays.asList(types));
  }

  @Override
  public Iterable<SeenObject> search(final Collection<String> types) {
    return search(Predicates.in(types));
  }

  public Iterable<SeenObject> search(final Predicate<? super String> filter) {

    // For now the inefficient way  FIXME: use positional grid searches and
    // split queries for static/dynamic
    //
    // Actually, search of live objects should probably be a physics
    // query using some shape like a cone or sphere.

    // Eventually we will want to know look direction, etc.
    // For now just use a simple radius check
    double radius = PERCEPTION_RADIUS; // chickens are near-sighted in this demo

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
    for (final EntityId id : ed.findEntities(null, SpawnPosition.class, ShapeInfo.class, Mass.class)) {
      if (id.equals(mob)) {
        // We don't see ourselves
        continue;
      }
      SeenObject seen = trySeeRigidBody(id, radius, filter);
      if (seen == null) {
        seen = trySeeStaticBody(id, radius, filter);
      }
      if (seen != null) {
        results.add(seen);
      }
    }

    return results;
  }

  private SeenObject trySeeRigidBody(final EntityId id, final double radius, final Predicate<? super String> filter) {
    RigidBody<EntityId, MBlockShape> rb =
        physics.getPhysicsSpace().getBinIndex().getRigidBody(id);
    if (rb == null) {
      return null;
    }
    String type = MobSystem.getType(rb);
    if (!filter.apply(type)) {
      return null;
    }
    // for now, dumb distance that ignores object size
    double dist = rb.position.distance(getBody().position);
    if (dist > radius) {
      return null;
    }
    return new SeenObject(
        id, rb.position, rb.orientation, rb.getLinearVelocity(), rb.shape, type, dist);
  }

  private SeenObject trySeeStaticBody(final EntityId id, final double radius, final Predicate<? super String> filter) {
    StaticBody<EntityId, MBlockShape> sb =
        physics.getPhysicsSpace().getBinIndex().getStaticBody(id);
    if (sb == null) {
      return null;
    }
    String type = MobSystem.getType(sb);
    if (!filter.apply(type)) {
      return null;
    }
    double dist = sb.position.distance(getBody().position);
    if (dist > radius) {
      return null;
    }
    return new SeenObject(id, sb.position, sb.orientation, Vec3d.ZERO, sb.shape, type, dist);
  }

  @Override
  public SeenObject look(final EntityId id) {
    // Same cases as above and we'll still only deal with (1) and (2) for
    // the moment.
    double radius = PERCEPTION_RADIUS; // chickens are near-sighted in this demo
    RigidBody<EntityId, MBlockShape> rb = physics.getPhysicsSpace().getBinIndex().getRigidBody(id);
    if (rb != null) {
      // for now, dumb distance that ignores object size
      // Well, we don't ignore it completeley anymore but it is just
      // a gross approximation
      double size = rb.shape.getMass().getRadius();
      double dist = rb.position.distance(getBody().position);
      if (dist <= (radius + size)) {
        return new SeenObject(
            id,
            rb.position,
            rb.orientation,
            rb.getLinearVelocity(),
            rb.shape,
            MobSystem.getType(rb),
            dist);
      }
    }

    StaticBody<EntityId, MBlockShape> sb =
        physics.getPhysicsSpace().getBinIndex().getStaticBody(id);
    if (sb != null) {
      double dist = sb.position.distance(getBody().position);
      if (dist <= radius) {
        return new SeenObject(
            id, sb.position, sb.orientation, Vec3d.ZERO, sb.shape, MobSystem.getType(sb), dist);
      }
    }
    return null;
  }

  @Override
  public SeenObject look2(final EntityId id) {
    // Same cases as above and we'll still only deal with (1) and (2) for
    // the moment.
    double radius = PERCEPTION_RADIUS; // chickens are near-sighted in this demo
    RigidBody<EntityId, MBlockShape> rb = physics.getPhysicsSpace().getBinIndex().getRigidBody(id);
    if (rb != null) {
      // Well, we don't ignore it completeley anymore but it is just
      // a gross approximation
      double size = rb.shape.getMass().getRadius();
      double dist = rb.position.distance(getBody().position);
      log.info("look2({}) distance:{}", id, dist);
      if (dist <= (radius + size)) {
        return new SeenObject(
            id,
            rb.position,
            rb.orientation,
            rb.getLinearVelocity(),
            rb.shape,
            MobSystem.getType(rb),
            dist);
      }
    } else {
      log.info("look2() no body");
    }

    StaticBody<EntityId, MBlockShape> sb =
        physics.getPhysicsSpace().getBinIndex().getStaticBody(id);
    if (sb != null) {
      double dist = sb.position.distance(getBody().position);
      if (dist <= radius) {
        return new SeenObject(
            id, sb.position, sb.orientation, Vec3d.ZERO, sb.shape, MobSystem.getType(sb), dist);
      }
    }
    return null;
  }

  @Override
  public void say(final long startTime, final long endTime, final String text) {
    EntityId entity = ed.createEntity();
    ed.setComponents(entity, new Speech(mob, text), new Decay(startTime, endTime));
  }

  public void release() {
    // If we still have a body and we are still the driver
    // for it, then clear ourselves
    if (getBody() != null && getBody().getControlDriver() == this) {
      getBody().setControlDriver(null);
      // The callback to terminate will clear our body reference
    }
  }

  protected void killVerticalRotation(final RigidBody<EntityId, MBlockShape> body) {

    // Kill any non-yaw orientation
    body.orientation.toAngles(angles);
    if (MobDriverLogic.killNonYawAngles(angles)) {
      body.orientation.fromAngles(angles);
    }

    // Kill any non-yaw velocity
    Vec3d rot = body.getRotationalVelocity();
    if (rot.x != 0 || rot.z != 0) {
      rot.x = 0;
      // rot.y *= 0.95; // Let's see if we can dampen the spinning
      rot.z = 0;

      // Don't really need to set it back but just in case
      body.setRotationalVelocity(rot);
    }
    // The above is copied from UprightDriver and is probably only
    // temporary in its current form.  There may end up being cases
    // where we calculate what "up" is for a mob differently.
  }

  @Override
  public void update(final long frameTime, final double step) {
    RigidBody<EntityId, MBlockShape> body = getBody();
    if (log.isTraceEnabled()) {
      log.trace("update(" + step + ")  temperature:" + body.getTemperature());
    }

    killVerticalRotation(body);

    // Here we could early out when we get a handle on what 'no input' means

    if (facing != targetFacing) {
      stepFacingTowardsTarget(step);
    } else if (probe != null) {
      adjustTargetFacingFromProbe();
    }

    orientation.mult(move, desiredVelocity);

    if (desiredVelocity.lengthSq() > 0) {
      applyDesiredVelocityForce(body);
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

    if (animPump != null && step > 0) {
      updateAnimation(step);
    }
  }

  /** Move {@code facing} towards {@code targetFacing} by one step's worth, taking the short way around. */
  private void stepFacingTowardsTarget(final double step) {
    // Need to deal with the cases where facing is like 5 degrees
    // and targetFacing is 355 degrees.  Need to know to just turn
    // 10 degrees instead of going all the way around. The math kernel
    // returns a value already wrapped into [0, 2π).
    facing = MobDriverLogic.shortestArcFacing(facing, targetFacing, step, settings.getTurnSpeed());
    orientation.fromAngles(0, facing, 0);
  }

  /**
   * If we are already heading in our intended direction, query the probe and nudge
   * {@code targetFacing} away from any imminent obstacle. Strikes a balance between
   * making sure the mob heads where the AI is telling it while also elastically
   * walking around simple obstacles.
   */
  private void adjustTargetFacingFromProbe() {
    probe.reset();
    physics
        .getPhysicsSpace()
        .queryContacts(probe.position, probe.orientation, probe.shape, probe.filter, probe);
    if (probe.closest == null) {
      return;
    }
    // When left is positive, we want to turn right and when
    // left is negative we want to turn left... but I'm pretty sure
    // the x,z plane is backwards from what one might think.
    targetFacing += MobDriverLogic.probeTurnDelta(probe.turn);
  }

  /** Convert {@link #desiredVelocity} into a body force, and notify {@link #brain} on hard pushback. */
  private void applyDesiredVelocityForce(final RigidBody<EntityId, MBlockShape> body) {
    // Calculate how much our velocity has to change to reach
    // the desired velocity
    force.set(desiredVelocity).subtractLocal(body.getLinearVelocity());

    if (desiredVelocity.y < 0.0001) {
      // Don't kill our gravity unless we are using vertical thrust
      force.y = 0;
    }

    // We could kill vertical velocity here based on contacts, in water, etc.

    // Right now, we'll treat everything as ground contact
    force.multLocal(settings.getGroundImpulse() * (1.0 / body.getInverseMass()));

    body.addForceAtPoint(force, 0.1, body.position);

    // If our average velocity is low and we have pushback in the direction
    // we want to go then send and event to the brain and let it figure out
    // what to do.
    // Questionable but we filter out low values of max push back to avoid
    // auto-blocking when a mob is moving and already in glancing contact
    // with something.
    if (maxPushback > 0.2 && averageVelocity.lengthSq() < (0.001 * 0.001)) {
      log.info("***   max pushback:{}", maxPushback);
      brain.blocked(mostBlocked.contactNormal);
    }
  }

  /** Pick Idle vs Walk anim and tick the rig. Caller must guarantee {@code animPump != null && step > 0}. */
  private void updateAnimation(final double step) {
    // See which animation we should be using.
    final double speed = averageVelocity.length() / step;
    final MobDriverLogic.AnimChoice choice = MobDriverLogic.pickAnimAction(speed);
    if ("Idle".equals(choice.action)) {
      log.info("actual velocity:{}  averageVelocity:{}", actualVelocity, averageVelocity);
    }
    animPump.setCurrentAction(choice.action, choice.animSpeed);

    animPump.update(step);
    rigShape.update();
  }

  @Override
  public void newContact(final Contact<EntityId, MBlockShape> contact) {
    double push = -contact.contactNormal.dot(desiredVelocity);
    if (push > maxPushback) {
      mostBlocked = contact;
      maxPushback = push;
    }

    if (contact.getBody2() == null) {
      // We don't do anything with world contacts yet... but we will
      // eventually need them for auto-climbing, 'on ground' dection, etc.
      return;
    }

    RigidBody<EntityId, MBlockShape> rb = contact.body1;
    if (rb == getBody()) {
      rb = contact.getBody2();
    }
    String type = MobSystem.getType(rb);
    if (!brain.isInterestingTouch(type)) {
      return;
    }

    SeenObject object =
        new SeenObject(
            rb.id,
            rb.position,
            rb.orientation,
            rb.getLinearVelocity(),
            rb.shape,
            type,
            contact.penetration);
    brain.touch(new TouchEvent(object, contact.contactPoint, contact.contactNormal));
  }

  private class Probe implements ContactListener<EntityId, MBlockShape> {

    private final MBlockShape shape;
    private final Vec3d offset;
    private final Quatd orientation = new Quatd(); // mostly because we don't have an identity constant.
    private final QueryFilter filter = new QueryFilter(QueryFilter.TYPE_ALL);

    private final Vec3d position = new Vec3d();
    private double minDistanceSq;
    private Contact<EntityId, MBlockShape> closest;
    private final Vec3d relative = new Vec3d();
    private final Vec3d dir = new Vec3d();
    private final Vec3d left = new Vec3d();
    private double turn;

    public Probe(final ProbeInfo info) {
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

    public void newContact(final Contact<EntityId, MBlockShape> contact) {
      // See if it's a contact that we're even interested in

      if (contact.body1 == getBody() || contact.body2 == getBody()) {
        // Self contact
        return;
      }

      relative.set(contact.contactPoint).subtractLocal(getBody().position);

      // 'facing' or not depends on relative position and not our
      // mob's facing dir.
      double dotFacing = contact.contactNormal.dot(relative);
      if (dotFacing > 0) {
        // If facing is positive then that means the contact normal
        // points roughly in the same direction that we're facing
        // so it's the back side of something (from our perspective).
        return;
      }

      // Possible future "too close" check: relative.dot(dir) < threshold → return.

      double distSq = contact.contactPoint.distanceSq(getBody().position);
      if (distSq < minDistanceSq) {
        closest = contact;
        minDistanceSq = distSq;
        turn = relative.dot(left);
      }
    }
  }
}
