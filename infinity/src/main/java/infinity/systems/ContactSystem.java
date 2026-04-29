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

import com.google.common.reflect.TypeToken;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.AbstractShape;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.DynArray;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.config.ArenaConfig;
import infinity.es.CollisionCategory;
import infinity.es.Parent;
import infinity.es.Sensor;
import infinity.es.arena.ArenaId;
import infinity.es.ship.BounceRestitution;
import infinity.sim.CategoryFilter;
import infinity.sim.util.InfinityRunTimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A conctact system to handle the contacts we want to disable as the last delegate in the line.
 *
 * @author AFahrenholz
 */
public class ContactSystem<K, S extends AbstractShape> extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  static Logger log = LoggerFactory.getLogger(ContactSystem.class);
  private final DynArray<ContactListener<K, S>> listeners =
      new DynArray<>(new TypeToken<ContactListener<K, S>>() {});
  EntitySet categoryFilters;
  private EntityData ed;
  private MPhysSystem<?> physics;
  private ArenaSystem arenaSystem;

  @Override
  public void newContact(Contact contact) {
    final RigidBody<EntityId, MBlockShape> bodyOne = contact.body1;
    final AbstractBody<EntityId, MBlockShape> bodyTwo = contact.body2;

    log.debug("Contact between: {} and {}", bodyOne.id, bodyTwo != null ? bodyTwo.id : "null");
    // Body1 is always a rigidbody
    // If body two is not null, we are dealing with a collision between a rigidbody (body1) and a
    // rigidbody or a staticbody (body2)
    if (bodyTwo != null) {
      final EntityId one = bodyOne.id;
      final EntityId two = bodyTwo.id;

      // Sensor bodies (arena ghost spheres, future safe-zones, gravity wells, ...) generate
      // contact events but must never participate in collision response. Disable the
      // contact before the resolver sees it; downstream listeners (ArenaMembershipSystem
      // etc.) still receive newContact via the listener fan-out below.
      final boolean oneSensor = ed.getComponent(one, Sensor.class) != null;
      final boolean twoSensor = ed.getComponent(two, Sensor.class) != null;
      if (oneSensor || twoSensor) {
        contact.disable();
        log.debug(
            "Sensor contact: {} (sensor={}) vs {} (sensor={}) at {}, disabled={}",
            one, oneSensor, two, twoSensor, contact.contactPoint, !contact.isEnabled());
        // Fall through to the listener fan-out so ArenaMembershipSystem etc. can observe.
      } else if (!categoryFilterAllowsContact(one, two)) {
        contact.disable();
        log.debug(
            "Category filter contact: {} vs {} at {}, disabled={}",
            one, two, contact.contactPoint, !contact.isEnabled());
        return;
      } else if (parentChildContact(one, two)) {
        contact.disable();
        log.debug(
            "Parent child contact: {} (sensor={}) vs {} (sensor={}) at {}, disabled={}",
            one, oneSensor, two, twoSensor, contact.contactPoint, !contact.isEnabled());
        return;
      } else {
        // Body-vs-body contact with no rejection — proceeds to resolver normally
        // (ship-vs-ship, ship-vs-projectile, etc.). Common case, not an error.
        log.debug(
            "Body-vs-body contact: {} vs {} at {}", one, two, contact.contactPoint);
      }
    } else {
      // Bounce off a static map block. Read the body's own per-ship restitution
      // (projected from ShipConfig at spawn); fall back to a perfectly-elastic
      // bounce when the body has no BounceRestitution component (non-ship
      // dynamics: projectiles, debris, anything not driven by ShipSpawnSystem).
      final BounceRestitution bounce = ed.getComponent(bodyOne.id, BounceRestitution.class);
      contact.restitution = bounce != null ? bounce.getRestitution() : 1.0;

      // Apply tangential damping ourselves rather than via the resolver's
      // friction model. Reason: the resolver computes a friction impulse
      // tangent to the contact normal at the contact point, then applies
      // r × impulse as a torque (Contact.calculateVelocityChange). For a
      // sphere body that torque rotates the body's heading toward the wall
      // — a real rigid-body effect, but wrong for arcade ship physics where
      // the player owns heading. By keeping contact.friction = 0 we get a
      // pure normal impulse (r ∥ n on a sphere → zero torque), and we
      // separately scale the body's tangential velocity component here.
      // Contact callbacks fire after integration but before resolver, so
      // the resolver sees the damped velocity and bounces from there.
      final double wallFriction = wallFrictionFor(bodyOne.id);
      if (wallFriction > 0.0) {
        final Vec3d v = bodyOne.getLinearVelocity();
        final Vec3d n = contact.contactNormal;
        final double vDotN = v.dot(n);
        // new_v = v_normal + (1 - wallFriction) * v_tangent
        //       = (1 - wallFriction) * v + wallFriction * (v·n) * n
        final double k = 1.0 - wallFriction;
        bodyOne.setLinearVelocity(
            new Vec3d(
                k * v.x + wallFriction * vDotN * n.x,
                k * v.y + wallFriction * vDotN * n.y,
                k * v.z + wallFriction * vDotN * n.z));
      }
      contact.friction = 0.0;
      log.debug(
          "Body vs static-map contact: {} at {}, restitution={}, wallFriction={}",
          bodyOne.id, contact.contactPoint, contact.restitution, wallFriction);
    }

    // Now that we have filtered the basics, lets send it to the various systems listening for
    // contacts
    for (ContactListener l : listeners) {
      l.newContact(contact);
    }
  }

  /**
   * Resolve the arena-scope wall-friction for the body involved in a body-vs-static
   * contact. Bodies without an {@link ArenaId} (projectiles, debris, anything not
   * placed inside a loaded arena) get {@link ArenaConfig#EMPTY}'s default of
   * {@code 0.0}, preserving the historical frictionless behaviour.
   */
  private double wallFrictionFor(final EntityId bodyId) {
    final ArenaId arenaId = ed.getComponent(bodyId, ArenaId.class);
    if (arenaId == null) {
      return ArenaConfig.EMPTY.wallFriction();
    }
    return arenaSystem.getArenaConfig(arenaId.getArena()).wallFriction();
  }

  /**
   * This method checks if the two entities are allowed to collide based on their category filters.
   * We only return false if the filters explicity disallow the collision. If the filters do not
   * contain the entities, we return true.
   *
   * @param one The first entity
   * @param two The second entity
   * @return false if the filters disallow the collision, true otherwise
   */
  private boolean categoryFilterAllowsContact(EntityId one, EntityId two) {
    if (categoryFilters.containsId(two) && categoryFilters.containsId(one)) {
      final CategoryFilter filterOne =
          categoryFilters.getEntity(one).get(CollisionCategory.class).getFilter();
      final CategoryFilter filterTwo =
          categoryFilters.getEntity(two).get(CollisionCategory.class).getFilter();
      if (!filterTwo.isAllowed(filterOne)) {
        log.debug(
            "Disabling contact because of category filters:" + filterOne + " and: " + filterTwo);
      }
      return filterTwo.isAllowed(filterOne);
    }
    return true;
  }

  /**
   * This method checks if the two entities are parent and child of each other. If they are, we
   * return true, otherwise false.
   *
   * @param one The first entity
   * @param two The second entity
   * @return true if the entities are parent and child of each other, false otherwise
   */
  private boolean parentChildContact(EntityId one, EntityId two) {
    boolean res = false;
    final Parent parentOfOne = ed.getComponent(one, Parent.class);
    if (parentOfOne != null && parentOfOne.getParentEntityId().compareTo(two) == 0) {
      res = true;
    }

    final Parent parentOfTwo = ed.getComponent(two, Parent.class);
    if (parentOfTwo != null && parentOfTwo.getParentEntityId().compareTo(one) == 0) {
      res = true;
    }

    if (res) {
      log.debug("Disabling contact because of parent child relationship: " + one + " and: " + two);
    }

    return res;
  }

  @Override
  public void update(final SimTime time) {
    super.update(time); // To change body of generated methods, choose Tools | Templates.

    categoryFilters.applyChanges();
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    if (ed == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires an EntityData object.");
    }
    physics = getSystem(MPhysSystem.class);
    if (physics == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the MPhysSystem system.");
    }
    arenaSystem = getSystem(ArenaSystem.class);
    if (arenaSystem == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the ArenaSystem system.");
    }

    categoryFilters = ed.getEntities(CollisionCategory.class);
  }

  @Override
  protected void terminate() {
    categoryFilters.release();
    categoryFilters = null;
  }

  public void addListener(ContactListener listener) {
    this.listeners.add(listener);
  }

  public void removeListener(ContactListener listener) {
    this.listeners.remove(listener);
  }
}
