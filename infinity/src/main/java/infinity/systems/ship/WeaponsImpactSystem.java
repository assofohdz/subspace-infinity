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
import infinity.es.ship.Health;
import infinity.es.ship.actions.Thor;
import infinity.es.ship.weapons.Bounce;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.ContactSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replacement-as-Mutation pilot — impact-side carve from the legacy
 * {@code WeaponsSystem} god-class. Implements
 * {@link ContactListener} for projectile-vs-body and projectile-vs-world
 * contacts; pairs damage-bearers with health-bearers, gates against
 * {@link ProximityFuse} arming, and dispatches detonation to
 * {@link WeaponsReaperSystem}.
 *
 * <p>RaM canonical-writer ledger for this system:
 * <ul>
 *   <li><b>{@link Bounce} decrement</b> (single writer): on a bouncing
 *       projectile's wall hit, this system either decrements the
 *       {@code Bounce} count or removes the component entirely. No other
 *       system writes {@code Bounce}; single-writer trivially satisfied.
 *   <li><b>Detonation dispatch</b>: forwards to
 *       {@link WeaponsReaperSystem#detonate} — the reaper owns
 *       {@link com.simsilica.es.common.Decay}-stamping, explosion-entity
 *       spawning, and damage intent emission. This system never writes
 *       {@code Decay} or {@link Damage}-shaped components on its own.
 *   <li><b>Sim-time tick capture</b>: the {@link ContactListener} callback
 *       fires outside the system's {@link #update(SimTime)} cycle, so this
 *       system caches the latest tick time in {@link #update} for the
 *       contact callback to read. Mirrors the legacy
 *       {@code WeaponsSystem.time} field bit-for-bit.
 * </ul>
 *
 * <p><b>Behaviour-preservation contract</b>: the contact-handling shape is
 * lifted unchanged from {@code WeaponsSystem.newContact} — same body / damage
 * / health pairing logic, same proximity-fuse gate, same wall-hit-bypass for
 * bombs.
 *
 * @author AFahrenholz
 */
public class WeaponsImpactSystem extends BaseInfinitySystem
    implements ContactListener<EntityId, MBlockShape> {

  static final Logger log = LoggerFactory.getLogger(WeaponsImpactSystem.class);

  private EntityData ed;
  private WeaponsReaperSystem reaper;
  private long lastTickNanos;

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
    // Cache the tick time for the contact callback. ContactListener.newContact
    // fires from physics, not from update-order, so the system needs a
    // stable "now" to pass into Reaper.detonate.
    lastTickNanos = tpf.getTime();
  }

  /**
   * The case of our bomb hitting ourselves is handled in the
   * {@link ContactSystem}. Here we want to handle the case where an entity
   * that has a {@link Damage} component hits an entity that has a
   * {@link Health} component. We also want to handle the case where a
   * projectile hits the world.
   *
   * <p>We cannot be sure the EntitySets are updated, so we inquire about the
   * entities here and now via {@code ed.getEntity}.
   *
   * <p>Slice 9a — when the damage-bearing entity carries a
   * {@link infinity.es.SplashDamage} marker (today: bombs), the damage path
   * switches from "single-target point damage at the contact" to "scan all
   * {@link Health}-bearing bodies inside the splash radius and damage each
   * one, gated by the arena's {@code friendlyFire} mode." Direct-hit damage
   * on entities without {@code SplashDamage} (bullets, burst, mines,
   * gravity-bomb fall-through) goes through the same friendly-fire gate but
   * with the stricter "mode 2 only" rule.
   *
   * @param contact the contact
   */
  @Override
  @SuppressWarnings("unchecked")
  public void newContact(final Contact contact) {
    final RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    final AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    final EntityId idOne = body1.id;
    final Entity entity1 = ed.getEntity(idOne, Damage.class, Bounce.class, Thor.class, Health.class);

    if (body2 instanceof RigidBody) {
      handleProjectileVsBody(contact, body1, (RigidBody<EntityId, MBlockShape>) body2, entity1);
    } else if (body2 == null
        && entity1.get(Damage.class) != null
        && entity1.get(Thor.class) == null) {
      handleProjectileVsWorld(contact, idOne, entity1);
    }
  }

  /** Body-vs-body branch: pair damage+health entities, gate on proximity fuse, detonate. */
  private void handleProjectileVsBody(
      final Contact contact,
      final RigidBody<EntityId, MBlockShape> body1,
      final RigidBody<EntityId, MBlockShape> body2,
      final Entity entity1) {
    final EntityId idTwo = body2.id;
    final Entity entity2 = ed.getEntity(idTwo, Damage.class, Health.class);

    log.debug("WeaponsImpactSystem contact detected between: {} and {}", body1.id, body2.id);

    final Entity damageEntity;
    final Entity energyEntity;
    if (entity1.get(Damage.class) != null && entity2.get(Health.class) != null) {
      damageEntity = entity1;
      energyEntity = entity2;
    } else if (entity2.get(Damage.class) != null && entity1.get(Health.class) != null) {
      damageEntity = entity2;
      energyEntity = entity1;
    } else {
      return;
    }

    // Slice 9b — proximity-fuse bombs that haven't yet armed must NOT detonate
    // on direct body contact with a ship; arming + fuse + detonation flow
    // through ProximityFuseSystem instead. Disabling the contact here means
    // the projectile glides past the ship until the per-tick proximity scan
    // fires. Wall hits + contacts on already-armed bombs fall through to the
    // standard detonation path below.
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

  /** body2==null branch: projectile hit the world. Bounce if marked, else detonate. */
  private void handleProjectileVsWorld(
      final Contact contact, final EntityId idOne, final Entity entity1) {
    // body2 = null means body1 is hitting the world.
    // Thors are handled in the action system.
    final Damage damage = entity1.get(Damage.class);

    final Bounce bounce = ed.getComponent(idOne, Bounce.class);
    if (bounce != null) {
      // Retain all energy in the contact.
      contact.restitution = 1;
      // Remove all friction so ingoing angle and outgoing angle are the same.
      contact.friction = 0;
      if (bounce.getBounces() == 1) {
        ed.removeComponent(idOne, Bounce.class);
      } else {
        ed.setComponent(idOne, bounce.decreaseBounces());
      }
    } else {
      // Wall-hit detonation: walls bypass the proximity-fuse gate (canonical
      // Subspace — bombs detonate immediately on wall contact regardless of
      // arm state) and fall straight through to the splash / direct path.
      reaper.detonate(idOne, damage, contact.contactPoint, null, lastTickNanos);
      contact.disable();
    }
  }
}
