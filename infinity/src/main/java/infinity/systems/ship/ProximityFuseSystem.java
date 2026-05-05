// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Damage;
import infinity.es.Frequency;
import infinity.es.Parent;
import infinity.es.ProximityArmed;
import infinity.es.ProximityFuse;
import infinity.es.ship.Health;
import infinity.sim.util.InfinityRunTimeException;

/**
 * Slice 9b — proximity arming + fuse for projectiles carrying
 * {@link ProximityFuse} (today: bombs whose firing arena authored
 * {@code [Bomb] ProximityDistance} + {@code BombExplodeDelay}).
 *
 * <p>Per-tick scan. Two phases share one EntitySet of in-flight
 * proximity-fuse projectiles:
 * <ol>
 *   <li><b>Arming</b> — for projectiles without {@link ProximityArmed},
 *       distance-scan all {@link Health}-bearing entities. If any enemy
 *       sits inside {@code ProximityFuse.radiusWorldUnits}, stamp
 *       {@link ProximityArmed} with the current sim-time nanos. The
 *       projectile's own owner ({@link Parent}) is excluded so a bomb
 *       doesn't arm on its firer.
 *   <li><b>Detonation</b> — for projectiles with {@link ProximityArmed},
 *       once {@code now − armedAt ≥ fuseMs}, delegate detonation to
 *       {@link WeaponsSystem#detonateProjectile} (shared with the
 *       contact-path detonation in slice 9a). The projectile's body
 *       position at the time of fuse-end becomes the explosion centre.
 * </ol>
 *
 * <p><b>FF gate (canonical Subspace VIE):</b> only enemies trigger
 * arming. Same-team ships glide past the projectile regardless of arena
 * {@code friendlyFire} mode — that mode is a <em>damage</em> gate, not
 * an <em>arming</em> gate. Enabling friendlies-arm-at-FF2 is a
 * polish-bag follow-up.
 *
 * <p><b>Wall hits bypass this system.</b> The contact-path detonation in
 * {@code WeaponsSystem.newContact} catches {@code body2 == null} (world
 * collision) and detonates immediately regardless of arm state, matching
 * Subspace canon (bombs explode on wall touch even when un-armed).
 *
 * <p><b>Deviation from canon:</b> REFERENCE.md says the bomb explodes
 * "immediate if ship leaves trigger area" once armed. Infinity runs the
 * fuse to completion regardless. Operator-noticeable on near-miss
 * fly-throughs only — polish-bag.
 */
public class ProximityFuseSystem extends AbstractGameSystem {

  private EntityData ed;
  private WeaponsSystem weaponsSystem;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private EntitySet fuseProjectiles;
  private EntitySet potentialVictims;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    if (ed == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires an EntityData object.");
    }
    weaponsSystem = getSystem(WeaponsSystem.class);
    if (weaponsSystem == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the WeaponsSystem.");
    }
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> phys = getSystem(MPhysSystem.class);
    if (phys == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the MPhysSystem system.");
    }
    physicsSpace = phys.getPhysicsSpace();

    fuseProjectiles =
        ed.getEntities(ProximityFuse.class, Damage.class, Parent.class);
    potentialVictims = ed.getEntities(Health.class);
  }

  @Override
  protected void terminate() {
    fuseProjectiles.release();
    fuseProjectiles = null;
    potentialVictims.release();
    potentialVictims = null;
  }

  @Override
  public void update(final SimTime time) {
    fuseProjectiles.applyChanges();
    potentialVictims.applyChanges();

    if (fuseProjectiles.isEmpty()) {
      return;
    }

    final long nowSimNanos = time.getTime();
    for (final Entity projectile : fuseProjectiles) {
      final EntityId projectileId = projectile.getId();
      final ProximityArmed armed = ed.getComponent(projectileId, ProximityArmed.class);
      if (armed == null) {
        tryArm(projectile, nowSimNanos);
      } else {
        final ProximityFuse fuse = projectile.get(ProximityFuse.class);
        if (fuseElapsed(armed.getArmedAtSimNanos(), nowSimNanos, fuse.getFuseMs())) {
          detonate(projectile, nowSimNanos);
        }
      }
    }
  }

  /**
   * Distance-scan for an enemy {@link Health}-bearer inside the projectile's
   * proximity radius. If found, stamp {@link ProximityArmed} so the next
   * tick's detonation phase fires after {@code fuseMs} elapses. The
   * projectile's owner ({@link Parent}) is excluded so it never arms on
   * its own firer.
   */
  private void tryArm(final Entity projectile, final long nowSimNanos) {
    final EntityId projectileId = projectile.getId();
    final ProximityFuse fuse = projectile.get(ProximityFuse.class);
    final double radius = fuse.getRadiusWorldUnits();
    if (radius <= 0.0) {
      return;
    }
    final RigidBody<EntityId, MBlockShape> projectileBody =
        physicsSpace.getBinIndex().getRigidBody(projectileId);
    if (projectileBody == null) {
      return;
    }
    final Vec3d projectilePos = projectileBody.position;
    final EntityId ownerId = projectile.get(Parent.class).getParentEntityId();
    final Frequency ownerFreq =
        ownerId == null ? null : ed.getComponent(ownerId, Frequency.class);
    final Integer ownerFreqValue = ownerFreq == null ? null : ownerFreq.getFrequency();
    final double radiusSq = radius * radius;

    for (final Entity victim : potentialVictims) {
      final EntityId victimId = victim.getId();
      if (victimId.equals(ownerId)) {
        continue; // never arm on the firing ship
      }
      final Frequency victimFreq = ed.getComponent(victimId, Frequency.class);
      final Integer victimFreqValue = victimFreq == null ? null : victimFreq.getFrequency();
      if (!shouldArmOn(ownerFreqValue, victimFreqValue)) {
        continue; // canonical: same-team ships don't arm proximity bombs
      }
      final RigidBody<EntityId, MBlockShape> victimBody =
          physicsSpace.getBinIndex().getRigidBody(victimId);
      if (victimBody == null) {
        continue;
      }
      final Vec3d vp = victimBody.position;
      final double dx = vp.x - projectilePos.x;
      final double dy = vp.y - projectilePos.y;
      final double dz = vp.z - projectilePos.z;
      if (dx * dx + dy * dy + dz * dz > radiusSq) {
        continue;
      }
      ed.setComponent(projectileId, new ProximityArmed(nowSimNanos));
      return;
    }
  }

  /**
   * Fuse expired — read the projectile's current body position and delegate
   * the splash + explosion + cleanup to {@link WeaponsSystem#detonateProjectile}
   * (shared with the contact-path detonation seam from slice 9a).
   */
  private void detonate(final Entity projectile, final long nowSimNanos) {
    final EntityId projectileId = projectile.getId();
    final RigidBody<EntityId, MBlockShape> body =
        physicsSpace.getBinIndex().getRigidBody(projectileId);
    if (body == null) {
      // Projectile already gone (e.g. aliveTime Decay reaped it this tick).
      // Canonical Decay reaper handled cleanup; nothing to do.
      return;
    }
    weaponsSystem.detonateProjectile(
        projectileId, projectile.get(Damage.class), body.position, null, nowSimNanos);
  }

  /**
   * Pure-function arming gate: true when {@code victimFreq} is a valid
   * arming target for a projectile fired by {@code ownerFreq}. Same-team
   * match returns false (canonical Subspace — friendly ships don't arm
   * proximity bombs regardless of arena {@code friendlyFire} mode). A
   * {@code null} freq on either side means "no team" (NPC / debris) and
   * the projectile arms.
   *
   * <p>Exposed package-private so unit tests can pin the arming-gate
   * tri-state without bringing up an ECS fixture (mirrors
   * {@link WeaponsSystem#shouldDamageVictim} which does the same for the
   * damage gate).
   */
  static boolean shouldArmOn(final Integer ownerFreq, final Integer victimFreq) {
    if (ownerFreq == null || victimFreq == null) {
      return true;
    }
    return !ownerFreq.equals(victimFreq);
  }

  /**
   * Pure-function fuse-elapsed check: true once {@code now − armedAt} has
   * reached {@code fuseMs} milliseconds (in nanos). Exposed package-private
   * so unit tests can pin the deadline arithmetic without bringing up the
   * full system loop.
   */
  static boolean fuseElapsed(
      final long armedAtSimNanos, final long nowSimNanos, final long fuseMs) {
    return nowSimNanos - armedAtSimNanos >= fuseMs * 1_000_000L;
  }
}
