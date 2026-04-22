/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.StaticBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.GravityWell;

/**
 * A system to handle gravity wells.
 *
 * @author AFahrenholz
 */
public class GravitySystem extends AbstractGameSystem implements ContactListener {

  private SimTime time;

  private EntityData ed;
  private EntitySet gravityWells;
  // A set to map from the pulling gravity wells to a pushing gravity well
  private ContactSystem contactSystem;

  protected void initialize() {
    this.ed = getSystem(EntityData.class);

    this.contactSystem = getSystem(ContactSystem.class);

    this.contactSystem.addListener(this);

    gravityWells = ed.getEntities(GravityWell.class, BodyPosition.class);
  }

  protected void terminate() {
    gravityWells.release();
    gravityWells = null;
  }

  @Override
  public void start() {
    // TODO Auto-generated method stub
  }

  @Override
  public void stop() {
    // TODO Auto-generated method stub
  }

  @Override
  public void update(SimTime tpf) {
    time = tpf;
  }

  @Override
  public void newContact(Contact contact) {

    RigidBody body1 = contact.body1;
    AbstractBody body2 = contact.body2;

    // Check if body2 is null (if so we are colliding a body with the world and dont want to do
    // anything here)
    if (body2 instanceof StaticBody) {
      EntityId one = (EntityId) body1.id;
      EntityId two = (EntityId) body2.id;

      // get GravityWell from body2
      GravityWell gw = ed.getComponent(two, GravityWell.class);

      // Check if GravityWell is null, if so, this isn't a contact for us to handle
      if (gw == null) {
        return;
      }

      //For the ship we want the latest positiom
      Vec3d bodyLocation = ed.getComponent(one, BodyPosition.class).getLastLocation();
      //For the wormhole we want the spawn position because it is a static object
      Vec3d wormholeLocation = ed.getComponent(two, SpawnPosition.class).getLocation();

      Vec3d difference = wormholeLocation.subtract(bodyLocation);
      Vec3d gravity = difference.normalize().multLocal(time.getTpf());
      double distance = difference.length();

      double wormholeGravity = gw.getForce();
      double gravityDistance = gw.getDistance();

      switch (gw.getGravityType()) {
        // Note 03-02-2023: I dont understand this math right now
        case GravityWell.PULL:
          gravity.multLocal(Math.abs(wormholeGravity));
          break;
        case GravityWell.PUSH:
          gravity.multLocal(1 * Math.abs(wormholeGravity));
          break;
        default:
          break;
      }

      gravity.multLocal(gravityDistance / distance);
      //Zero out the y-force
      gravity.y = 0;

      // Apply the gravity to the body
      body1.addForce(gravity);
      contact.disable();
    }
  }
}
