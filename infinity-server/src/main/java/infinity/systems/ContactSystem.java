// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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
import com.simsilica.sim.SimTime;
import infinity.config.ArenaConfig;
import infinity.es.CollisionCategory;
import infinity.es.Parent;
import infinity.es.Sensor;
import infinity.es.arena.ArenaId;
import infinity.es.ship.BounceRestitution;
import infinity.sim.CategoryFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Last-in-line contact dispatcher; fans out to per-system {@link ContactListener}s and disables contacts marked for skip. */
public class ContactSystem<K, S extends AbstractShape> extends BaseInfinitySystem
    implements ContactListener<EntityId, MBlockShape> {

  static Logger log = LoggerFactory.getLogger(ContactSystem.class);

  // MPhys-specific dynamic array; no JDK equivalent for TypeToken-keyed registration.
  @SuppressWarnings("PMD.LooseCoupling")
  private final DynArray<ContactListener<K, S>> listeners =
      new DynArray<>(new TypeToken<ContactListener<K, S>>() {});
  EntitySet categoryFilters;
  private EntityData ed;
  private ArenaSystem arenaSystem;

  @Override
  public void newContact(Contact contact) {
    final RigidBody<EntityId, MBlockShape> bodyOne = contact.body1;
    final AbstractBody<EntityId, MBlockShape> bodyTwo = contact.body2;

    if (log.isDebugEnabled()) {
      log.debug("Contact between: {} and {}", bodyOne.id, bodyTwo != null ? bodyTwo.id : "null");
    }
    // Body1 is always a rigidbody. If body two is not null, we are dealing with
    // a collision between a rigidbody (body1) and a rigidbody or a staticbody
    // (body2); otherwise it's a wall hit (static map block).
    final boolean fanOut;
    if (bodyTwo != null) {
      fanOut = handleBodyVsBody(contact, bodyOne, bodyTwo);
    } else {
      handleBodyVsStaticMap(contact, bodyOne);
      fanOut = true;
    }
    if (!fanOut) {
      return;
    }

    // Now that we have filtered the basics, lets send it to the various systems listening for
    // contacts
    for (ContactListener l : listeners) {
      l.newContact(contact);
    }
  }

  /**
   * Body-vs-body filter pipeline: sensor / category-filter / parent-child
   * rejection. Returns {@code true} if the contact should still fan out to
   * downstream listeners (sensor case + body-vs-body OK), {@code false} on
   * category-filter or parent-child rejection (early-return).
   */
  private boolean handleBodyVsBody(
      final Contact contact,
      final RigidBody<EntityId, MBlockShape> bodyOne,
      final AbstractBody<EntityId, MBlockShape> bodyTwo) {
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
      if (log.isDebugEnabled()) {
        log.debug(
            "Sensor contact: {} (sensor={}) vs {} (sensor={}) at {}, disabled={}",
            one, oneSensor, two, twoSensor, contact.contactPoint, !contact.isEnabled());
      }
      // Fall through to the listener fan-out so ArenaMembershipSystem etc. can observe.
      return true;
    }
    if (!categoryFilterAllowsContact(one, two)) {
      contact.disable();
      if (log.isDebugEnabled()) {
        log.debug(
            "Category filter contact: {} vs {} at {}, disabled={}",
            one, two, contact.contactPoint, !contact.isEnabled());
      }
      return false;
    }
    if (parentChildContact(one, two)) {
      contact.disable();
      if (log.isDebugEnabled()) {
        log.debug(
            "Parent child contact: {} (sensor={}) vs {} (sensor={}) at {}, disabled={}",
            one, oneSensor, two, twoSensor, contact.contactPoint, !contact.isEnabled());
      }
      return false;
    }
    // Body-vs-body contact with no rejection — proceeds to resolver normally
    // (ship-vs-ship, ship-vs-projectile, etc.). Common case, not an error.
    if (log.isDebugEnabled()) {
      log.debug("Body-vs-body contact: {} vs {} at {}", one, two, contact.contactPoint);
    }
    return true;
  }

  /**
   * Wall-hit handler: bounce off a static map block. Read the body's own
   * per-ship restitution (projected from ShipConfig at spawn); fall back to
   * a perfectly-elastic bounce when the body has no {@link BounceRestitution}
   * component (non-ship dynamics: projectiles, debris, anything not driven
   * by ShipSpawnSystem). Then optionally apply tangential damping per
   * {@link #applyWallTangentialDamping}.
   */
  private void handleBodyVsStaticMap(
      final Contact contact, final RigidBody<EntityId, MBlockShape> bodyOne) {
    final BounceRestitution bounce = ed.getComponent(bodyOne.id, BounceRestitution.class);
    contact.restitution = bounce != null ? bounce.getRestitution() : 1.0;

    final double wallFriction = wallFrictionFor(bodyOne.id);
    if (wallFriction > 0.0) {
      applyWallTangentialDamping(contact, bodyOne, wallFriction);
    }
    contact.friction = 0.0;
    log.debug(
        "Body vs static-map contact: {} at {}, restitution={}, wallFriction={}",
        bodyOne.id, contact.contactPoint, contact.restitution, wallFriction);
  }

  /**
   * Apply tangential damping ourselves rather than via the resolver's
   * friction model. Reason: the resolver computes a friction impulse
   * tangent to the contact normal at the contact point, then applies
   * r × impulse as a torque (Contact.calculateVelocityChange). For a
   * sphere body that torque rotates the body's heading toward the wall
   * — a real rigid-body effect, but wrong for arcade ship physics where
   * the player owns heading. By keeping contact.friction = 0 we get a
   * pure normal impulse (r ∥ n on a sphere → zero torque), and we
   * separately scale the body's tangential velocity component here.
   * Contact callbacks fire after integration but before resolver, so
   * the resolver sees the damped velocity and bounces from there.
   */
  private static void applyWallTangentialDamping(
      final Contact contact,
      final RigidBody<EntityId, MBlockShape> bodyOne,
      final double wallFriction) {
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
            "Disabling contact because of category filters:{} and: {}", filterOne, filterTwo);
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
      log.debug("Disabling contact because of parent child relationship: {} and: {}", one, two);
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
    ed = requireSystem(EntityData.class);
    requireSystem(MPhysSystem.class);
    arenaSystem = requireSystem(ArenaSystem.class);

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
