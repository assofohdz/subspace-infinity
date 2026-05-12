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
import com.simsilica.sim.SimTime;
import infinity.es.Damage;
import infinity.es.Frequency;
import infinity.es.Parent;
import infinity.es.ProximityArmed;
import infinity.es.ProximityFuse;
import infinity.es.ship.Energy;
import infinity.systems.BaseInfinitySystem;

/** Proximity arm + fuse for {@link ProximityFuse} projectiles; enemy-only arming (canonical). Diverges from REFERENCE.md: fuse runs to completion even if victim leaves. */
public class ProximityFuseSystem extends BaseInfinitySystem {

  private EntityData ed;
  private WeaponsReaperSystem weaponsReaperSystem;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private EntitySet fuseProjectiles;
  private EntitySet potentialVictims;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    weaponsReaperSystem = requireSystem(WeaponsReaperSystem.class);
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> phys = requireSystem(MPhysSystem.class);
    physicsSpace = phys.getPhysicsSpace();

    fuseProjectiles =
        ed.getEntities(ProximityFuse.class, Damage.class, Parent.class);
    potentialVictims = ed.getEntities(Energy.class);
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

  /** Stamp {@link ProximityArmed} if an enemy Health-bearer sits inside radius; owner excluded. */
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

  private boolean victimWouldArm(
      final EntityId victimId,
      final EntityId ownerId,
      final Integer ownerFreqValue,
      final Vec3d projectilePos,
      final Vec3d victimPos,
      final double radiusSq) {
    if (victimId.equals(ownerId) || !potentialVictims.containsId(victimId)
        || !shouldArmOn(ownerFreqValue, freqValueOf(victimId))) {
      return false;
    }
    final double dx = victimPos.x - projectilePos.x;
    final double dy = victimPos.y - projectilePos.y;
    final double dz = victimPos.z - projectilePos.z;
    return dx * dx + dy * dy + dz * dz <= radiusSq;
  }

  private Integer freqValueOf(final EntityId id) {
    if (id == null) {
      return null;
    }
    final Frequency f = ed.getComponent(id, Frequency.class);
    return f == null ? null : f.getFrequency();
  }

  private void detonate(final Entity projectile, final long nowSimNanos) {
    final EntityId projectileId = projectile.getId();
    final RigidBody<EntityId, MBlockShape> body =
        physicsSpace.getBinIndex().getRigidBody(projectileId);
    if (body == null) {
      return;
    }
    weaponsReaperSystem.detonate(
        projectileId, projectile.get(Damage.class), body.position, null, nowSimNanos);
  }

  /** Arming gate; same-team returns false (canonical), null freq = no team and arms. */
  static boolean shouldArmOn(final Integer ownerFreq, final Integer victimFreq) {
    if (ownerFreq == null || victimFreq == null) {
      return true;
    }
    return !ownerFreq.equals(victimFreq);
  }

  static boolean fuseElapsed(
      final long armedAtSimNanos, final long nowSimNanos, final long fuseMs) {
    return nowSimNanos - armedAtSimNanos >= fuseMs * 1_000_000L;
  }
}
