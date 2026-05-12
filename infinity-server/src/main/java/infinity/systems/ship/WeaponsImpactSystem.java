// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.SimTime;
import infinity.es.Damage;
import infinity.es.ProximityArmed;
import infinity.es.ProximityFuse;
import infinity.es.ship.Energy;
import infinity.es.ship.actions.Thor;
import infinity.es.ship.weapons.Bounce;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.ContactSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Projectile-vs-body and projectile-vs-world contacts; dispatches detonation to {@link WeaponsReaperSystem}. Canonical writer for {@link Bounce}. */
public class WeaponsImpactSystem extends BaseInfinitySystem
    implements ContactListener<EntityId, MBlockShape> {

  static final Logger log = LoggerFactory.getLogger(WeaponsImpactSystem.class);

  private EntityData ed;
  private WeaponsReaperSystem reaper;
  private volatile long lastTickNanos;

  @Override
  @SuppressWarnings("unchecked")
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    reaper = requireSystem(WeaponsReaperSystem.class);
    final ContactSystem<EntityId, MBlockShape> contactSystem = requireSystem(ContactSystem.class);
    contactSystem.addListener(this);
  }

  @Override
  protected void terminate() {
    final ContactSystem<EntityId, MBlockShape> contactSystem = getSystem(ContactSystem.class);
    if (contactSystem != null) {
      contactSystem.removeListener(this);
    }
  }

  @Override
  public void update(final SimTime tpf) {
    // Cache tick time — ContactListener.newContact fires from physics, not update-order.
    lastTickNanos = tpf.getTime();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void newContact(final Contact contact) {
    final RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    final AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    final EntityId idOne = body1.id;
    final Entity entity1 = ed.getEntity(idOne, Damage.class, Bounce.class, Thor.class, Energy.class);

    if (body2 instanceof RigidBody) {
      handleProjectileVsBody(contact, body1, (RigidBody<EntityId, MBlockShape>) body2, entity1);
    } else if (body2 == null
        && entity1.get(Damage.class) != null
        && entity1.get(Thor.class) == null) {
      handleProjectileVsWorld(contact, idOne, entity1);
    }
  }

  private void handleProjectileVsBody(
      final Contact contact,
      final RigidBody<EntityId, MBlockShape> body1,
      final RigidBody<EntityId, MBlockShape> body2,
      final Entity entity1) {
    final EntityId idTwo = body2.id;
    final Entity entity2 = ed.getEntity(idTwo, Damage.class, Energy.class);

    log.debug("WeaponsImpactSystem contact detected between: {} and {}", body1.id, body2.id);

    final Entity damageEntity;
    final Entity energyEntity;
    if (entity1.get(Damage.class) != null && entity2.get(Energy.class) != null) {
      damageEntity = entity1;
      energyEntity = entity2;
    } else if (entity2.get(Damage.class) != null && entity1.get(Energy.class) != null) {
      damageEntity = entity2;
      energyEntity = entity1;
    } else {
      return;
    }

    // Unarmed proximity-fuse bombs glide past ships — arming/fuse/detonation flow through ProximityFuseSystem.
    final ProximityFuse fuse = ed.getComponent(damageEntity.getId(), ProximityFuse.class);
    final boolean alreadyArmed =
        fuse != null && ed.getComponent(damageEntity.getId(), ProximityArmed.class) != null;
    if (fuse != null && !alreadyArmed) {
      contact.disable();
      return;
    }

    final Damage damage = damageEntity.get(Damage.class);
    reaper.detonate(
        damageEntity.getId(),
        damage,
        contact.contactPoint,
        energyEntity.getId(),
        lastTickNanos);
    contact.disable();
  }

  /** Projectile vs world: bounce if marked, else detonate (walls bypass the proximity-fuse gate per Subspace canon). */
  private void handleProjectileVsWorld(
      final Contact contact, final EntityId idOne, final Entity entity1) {
    final Damage damage = entity1.get(Damage.class);

    final Bounce bounce = ed.getComponent(idOne, Bounce.class);
    if (bounce != null) {
      contact.restitution = 1;
      contact.friction = 0;
      if (bounce.getBounces() == 1) {
        ed.removeComponent(idOne, Bounce.class);
      } else {
        ed.setComponent(idOne, bounce.decreaseBounces());
      }
    } else {
      reaper.detonate(idOne, damage, contact.contactPoint, null, lastTickNanos);
      contact.disable();
    }
  }
}
