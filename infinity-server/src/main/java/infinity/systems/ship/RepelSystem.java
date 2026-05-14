// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.Impulse;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import infinity.config.ZoneConfig;
import infinity.es.Frequency;
import infinity.es.Parent;
import infinity.es.Repellable;
import infinity.es.ship.actions.RepelDistance;
import infinity.es.ship.actions.RepelSpeed;
import infinity.settings.EngineConfigSystem;
import infinity.systems.ArenaSystem;
import infinity.systems.BaseInfinitySystem;
import java.util.Set;

/** One-shot {@link Impulse} on {@link Repellable} bodies inside a freshly spawned repel effect; FF gate via {@link ZoneConfig#repelFriendlies}. */
public class RepelSystem extends BaseInfinitySystem {

  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private ArenaSystem arenaSystem;
  private EngineConfigSystem engineConfigSystem;
  private EntitySet repelEffects;
  private EntitySet repellables;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> phys = requireSystem(MPhysSystem.class);
    physicsSpace = phys.getPhysicsSpace();
    arenaSystem = requireSystem(ArenaSystem.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);
    repelEffects =
        ed.getEntities(RepelSpeed.class, RepelDistance.class, Parent.class);
    repellables = ed.getEntities(Repellable.class);
  }

  @Override
  protected void terminate() {
    repelEffects.release();
    repelEffects = null;
    repellables.release();
    repellables = null;
  }

  @Override
  public void update(final SimTime time) {
    repelEffects.applyChanges();
    repellables.applyChanges();

    final Set<Entity> addedEffects = repelEffects.getAddedEntities();
    if (addedEffects.isEmpty()) {
      return;
    }

    final EngineConfig engineCfg = engineConfigSystem.get();
    final double scale = engineCfg.subspaceVelocityScale();
    final double maxJme = engineCfg.maxProjectileSpeedJme();
    final boolean repelFriendlies = arenaSystem.getZoneConfig().repelFriendlies();

    for (final Entity effect : addedEffects) {
      applyRepelImpulse(effect, scale, maxJme, repelFriendlies);
    }
  }

  private void applyRepelImpulse(
      final Entity effect,
      final double scale,
      final double maxJme,
      final boolean repelFriendlies) {
    final RigidBody<EntityId, MBlockShape> effectBody =
        physicsSpace.getBinIndex().getRigidBody(effect.getId());
    if (effectBody == null) {
      return;
    }
    final double radiusWorldUnits =
        effect.get(RepelDistance.class).getRadiusWorldUnits();
    final double magnitudeJme =
        WeaponsLogic.effectiveProjectileSpeed(
            effect.get(RepelSpeed.class).getSpeed(), scale, maxJme);
    if (radiusWorldUnits <= 0.0 || magnitudeJme == 0.0) {
      return;
    }

    final EntityId ownerId = effect.get(Parent.class).getParentEntityId();
    final Integer ownerFreqValue = freqValue(ownerId);
    final Vec3d effectPos = effectBody.position;
    final double radiusSq = radiusWorldUnits * radiusWorldUnits;

    for (final Entity victim : repellables) {
      tryRepelOne(victim, effectPos, ownerId, ownerFreqValue, radiusSq, magnitudeJme, repelFriendlies);
    }
  }

  private void tryRepelOne(
      final Entity victim,
      final Vec3d effectPos,
      final EntityId ownerId,
      final Integer ownerFreqValue,
      final double radiusSq,
      final double magnitudeJme,
      final boolean repelFriendlies) {
    final EntityId victimId = victim.getId();
    if (!shouldRepelVictim(victimId, ownerId, ownerFreqValue, freqValue(victimId), repelFriendlies)) {
      return;
    }
    final RigidBody<EntityId, MBlockShape> victimBody =
        physicsSpace.getBinIndex().getRigidBody(victimId);
    if (victimBody == null) {
      return;
    }
    final Vec3d vp = victimBody.position;
    final Vec3d impulse =
        planarImpulse(vp.x - effectPos.x, vp.z - effectPos.z, magnitudeJme, radiusSq);
    if (impulse != null) {
      ed.setComponent(victimId, new Impulse(impulse));
    }
  }

  /** FF + self-repel gate; victim==owner false; null Frequency always pushed. */
  static boolean shouldRepelVictim(
      final EntityId victimId,
      final EntityId ownerId,
      final Integer ownerFreq,
      final Integer victimFreq,
      final boolean repelFriendlies) {
    if (victimId.equals(ownerId)) {
      return false;
    }
    if (repelFriendlies) {
      return true;
    }
    if (ownerFreq == null || victimFreq == null) {
      return true;
    }
    return !ownerFreq.equals(victimFreq);
  }

  /** Planar (Y=0) impulse from offset (dx, dz); null if outside radius or at center. */
  static Vec3d planarImpulse(
      final double dx, final double dz, final double magnitudeJme, final double radiusSq) {
    final double distSq = dx * dx + dz * dz;
    if (distSq > radiusSq || distSq <= 0.0) {
      return null;
    }
    final double dist = Math.sqrt(distSq);
    return new Vec3d(dx / dist * magnitudeJme, 0.0, dz / dist * magnitudeJme);
  }

  private Integer freqValue(final EntityId id) {
    if (id == null) {
      return null;
    }
    final Frequency freq = ed.getComponent(id, Frequency.class);
    return freq == null ? null : freq.getFrequency();
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}
