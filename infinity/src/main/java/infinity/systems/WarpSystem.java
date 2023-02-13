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

package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.Filters;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Parent;
import infinity.es.WarpTouch;
import infinity.es.ship.Energy;
import infinity.es.ship.actions.WarpTo;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandBiConsumer;
import infinity.sim.CommandMonoConsumer;
import infinity.sim.GameEntities;
import infinity.sim.InfinityEntityBodyFactory;
import infinity.sim.util.InfinityRunTimeException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This system handles the warping of units. It is responsible for the
 * implementation of the warp command, and the warp touch component.
 *
 * @author Asser
 */
// FIXME: Implement collisionlistener interface and listen for collisions between warptouch entities
// and other warpable entities
public class WarpSystem extends AbstractGameSystem implements ContactListener {

  static Logger log = LoggerFactory.getLogger(WarpSystem.class);
  private final Pattern requestWarpToCenter = Pattern.compile("\\~warpCenter");
  private EntityData ed;
  private EntitySet warpTouchEntities;
  private EntitySet warpToEntities;
  private EntitySet canWarp;
  private PhysicsSpace physicsSpace;
  private InfinityEntityBodyFactory bodyFactory;

  @Override
  protected void initialize() {
    this.ed = getSystem(EntityData.class);

    if (getSystem(MPhysSystem.class) == null) {
      throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
    }
    physicsSpace = getSystem(MPhysSystem.class).getPhysicsSpace();

    bodyFactory = getSystem(InfinityEntityBodyFactory.class);

    warpTouchEntities = ed.getEntities(WarpTouch.class);
    warpToEntities = ed.getEntities(BodyPosition.class, WarpTo.class);

    canWarp = ed.getEntities(BodyPosition.class, Energy.class);

    // Register consuming methods for patterns
    getSystem(InfinityChatHostedService.class)
        .registerPatternBiConsumer(
            requestWarpToCenter,
            "The command to warp to the center of the arena is ~warpCenter",
            new CommandBiConsumer<>(AccessLevel.PLAYER_LEVEL, this::requestWarpToCenter));

    getSystem(ContactSystem.class).addListener(this);
  }

  @Override
  protected void terminate() {
    warpTouchEntities.release();
    warpTouchEntities = null;
  }

  @Override
  public void start() {
    // Auto generated method stub
  }

  @Override
  public void stop() {
    // Auto generated method stub
  }

  @Override
  public void update(SimTime tpf) {

    canWarp.applyChanges();
    warpTouchEntities.applyChanges();

    if (warpToEntities.applyChanges()) {
      for (Entity e : warpToEntities) {
        BodyPosition bodyPos = e.get(BodyPosition.class);
        Vec3d targetLocation = e.get(WarpTo.class).getTargetLocation();
        Vec3d originalLocation = bodyPos.getLastLocation();

        // This is the new method to teleport units
        physicsSpace.teleport(e.getId(), targetLocation, bodyPos.getLastOrientation());

        GameEntities.createWarpEffect(
            ed, e.getId(), physicsSpace, tpf.getTime(), originalLocation, 1000);
        GameEntities.createWarpEffect(
            ed, e.getId(), physicsSpace, tpf.getTime(), targetLocation, 1000);

//         Ensure that the unit is not moving after the warp
        RigidBody body = bodyFactory.getBody(e.getId());
        body.setLinearVelocity(Vec3d.ZERO);
        body.setRotationalVelocity(Vec3d.ZERO);
        body.setLinearAcceleration(Vec3d.ZERO);
        body.setRotationalAcceleration(0,0,0);
        body.clearAccumulators();

        ed.removeComponent(e.getId(), WarpTo.class);
      }
    }
  }

  /**
   * Lets entities request a warp to the center of the arena.
   *
   * @param avatarId requesting entity
   */
  public void requestWarpToCenter(EntityId entityId, EntityId avatarId) {
    // TODO: Check for full health
    ComponentFilter filter = Filters.fieldEquals(Parent.class, "parentEntity", entityId);
    EntitySet entitySet = ed.getEntities(filter, BodyPosition.class);

    if (entitySet.size() != 1) {
      throw new InfinityRunTimeException(
          "Entity " + entityId + " has " + entitySet.size() + " children. Expected 1.");
    } else {
      Entity child = entitySet.iterator().next();
      BodyPosition childBodyPos = child.get(BodyPosition.class);
      Vec3d lastLoc = childBodyPos.getLastLocation();

      Vec3d centerOfArena = getSystem(MapSystem.class).getCenterOfArena(lastLoc.x, lastLoc.z);
      WarpTo warpTo = new WarpTo(centerOfArena);
      ed.setComponent(child.getId(), warpTo);
    }
  }

    @Override
    public void newContact(Contact contact) {
        RigidBody body1 = contact.body1;
        AbstractBody body2 = contact.body2;

        //If body2 is null, then the contact is with the world and we should not handle this
        if (body2 == null){
            return;
        }

        EntityId body1Id = (EntityId) body1.id;
        EntityId body2Id = (EntityId) body2.id;

        //Warp body1 if body2 is a warp touch entity
        if (warpTouchEntities.containsId(body2Id)){
            WarpTouch warpTouch = warpTouchEntities.getEntity(body2Id).get(WarpTouch.class);
            WarpTo warpTo = new WarpTo(warpTouch.getTargetLocation());
            ed.setComponent(body1Id, warpTo);
        }
    }
}
