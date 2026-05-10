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
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.QueryFilter;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.SphereVolume;
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
 *       {@link WeaponsReaperSystem#detonate} (shared with the
 *       contact-path detonation in slice 9a; canonical writer for the
 *       projectile end-of-life {@link com.simsilica.es.common.Decay} stamp).
 *       The projectile's body position at the time of fuse-end becomes the
 *       explosion centre.
 * </ol>
 *
 * <p><b>FF gate (canonical Subspace VIE):</b> only enemies trigger
 * arming. Same-team ships glide past the projectile regardless of arena
 * {@code friendlyFire} mode — that mode is a <em>damage</em> gate, not
 * an <em>arming</em> gate. Enabling friendlies-arm-at-FF2 is a
 * polish-bag follow-up.
 *
 * <p><b>Wall hits bypass this system.</b> The contact-path detonation in
 * {@code WeaponsImpactSystem.newContact} catches {@code body2 == null} (world
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
  private WeaponsReaperSystem weaponsReaperSystem;
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
    weaponsReaperSystem = getSystem(WeaponsReaperSystem.class);
    if (weaponsReaperSystem == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the WeaponsReaperSystem.");
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
   *
   * <p>Spatial pre-filter via {@code mphys.PhysicsSpace#queryBounds}
   * (arch-review TD-3 — replaces the per-tick O(N) walk over every
   * Health-bearer with a bin-local active+inactive rigid-body scan).
   * The {@link #potentialVictims} EntitySet is preserved as the
   * Health-bearer gate (queryBounds returns ALL bodies — projectiles,
   * prizes, doors — which we filter to ships via {@code containsId}).
   * The strict point-distance check post-query preserves bit-exact
   * radius semantics (queryBounds inflates by {@code body.boundsRadius}
   * so it's a coarse pre-filter, not the final accept).
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
    final EntityId ownerId = projectile.get(Parent.class).getParentEntityId();
    final Integer ownerFreqValue = freqValueOf(ownerId);
    final double radiusSq = radius * radius;

    final SphereVolume sphere = new SphereVolume(projectileBody.position, radius);
    final QueryFilter<EntityId, MBlockShape> filter =
        new QueryFilter<>(
            QueryFilter.TYPE_ACTIVE | QueryFilter.TYPE_INACTIVE,
            body -> !body.id.equals(ownerId),
            body -> true);

    for (final AbstractBody<EntityId, MBlockShape> body : physicsSpace.queryBounds(sphere, filter)) {
      if (victimWouldArm(body.id, ownerId, ownerFreqValue, projectileBody.position,
          body.position, radiusSq)) {
        ed.setComponent(projectileId, new ProximityArmed(nowSimNanos));
        return;
      }
    }
  }

  /**
   * Per-victim arming check: same-team / self / out-of-radius / non-Health
   * are all skip cases. Returns {@code true} only when {@code victimId} is a
   * Health-bearing enemy inside {@code radiusSq} of {@code projectilePos}.
   * The body-position lookup happens at the call site (post-queryBounds);
   * this helper does the freq + strict distance gate only.
   */
  private boolean victimWouldArm(
      final EntityId victimId,
      final EntityId ownerId,
      final Integer ownerFreqValue,
      final Vec3d projectilePos,
      final Vec3d victimPos,
      final double radiusSq) {
    if (victimId.equals(ownerId)) {
      return false; // never arm on the firing ship (defense-in-depth; queryBounds filter also excludes)
    }
    if (!potentialVictims.containsId(victimId)) {
      return false; // not a Health-bearer (projectile, prize, door, …)
    }
    if (!shouldArmOn(ownerFreqValue, freqValueOf(victimId))) {
      return false; // canonical: same-team ships don't arm proximity bombs
    }
    final double dx = victimPos.x - projectilePos.x;
    final double dy = victimPos.y - projectilePos.y;
    final double dz = victimPos.z - projectilePos.z;
    return dx * dx + dy * dy + dz * dz <= radiusSq;
  }

  /** Read the {@link Frequency} value for {@code id}, treating null/missing as {@code null}. */
  private Integer freqValueOf(final EntityId id) {
    if (id == null) {
      return null;
    }
    final Frequency f = ed.getComponent(id, Frequency.class);
    return f == null ? null : f.getFrequency();
  }

  /**
   * Fuse expired — read the projectile's current body position and delegate
   * the splash + explosion + cleanup to {@link WeaponsReaperSystem#detonate}
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
    weaponsReaperSystem.detonate(
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
   * {@link WeaponsLogic#shouldDamageVictim} which does the same for the
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
