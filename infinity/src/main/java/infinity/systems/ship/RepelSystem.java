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
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import infinity.config.ZoneConfig;
import infinity.es.Frequency;
import infinity.es.Parent;
import infinity.es.Repellable;
import infinity.es.ship.actions.RepelDistance;
import infinity.es.ship.actions.RepelSpeed;
import infinity.settings.EngineConfigSystem;
import infinity.sim.util.InfinityRunTimeException;
import infinity.systems.ArenaSystem;
import java.util.Set;

/**
 * Slice S5 — applies a one-shot impulse to {@link Repellable} bodies in
 * range when a repel effect entity appears. Mirrors
 * {@link ProximityFuseSystem}'s structural pattern (per-tick scan,
 * {@link PhysicsSpace}-driven body lookup, FF-tri-state filter).
 *
 * <p><b>Triggering shape (Q1=a one-shot via {@code getAddedEntities()}):</b>
 * each tick after {@code applyChanges()} the system iterates only the
 * effect entities <em>added since last tick</em> — i.e., the one frame
 * after {@code ConsumableSystem.createRepel} stamped the effect.
 * Subsequent ticks ignore the same effect entirely (it persists in the
 * main set for the visual lifetime via {@link com.simsilica.es.common.Decay}
 * but never gets re-processed).
 *
 * <p><b>Self-repel filter (Q3):</b> the firing ship is at the explosion
 * center (distance ≈ 0); skipped via {@link Parent} on the effect entity.
 *
 * <p><b>FF semantics (Q4):</b> driven by zone-tier
 * {@link ZoneConfig#repelFriendlies}. When {@code true} (default,
 * Subspace canon), every {@code Repellable} in radius gets pushed.
 * When {@code false}, same-frequency ships are skipped; entities without
 * {@link Frequency} (bombs / mines / debris) are always pushed.
 *
 * <p><b>Falloff (Q5):</b> uniform impulse magnitude inside radius, no
 * distance attenuation — matches Subspace canon and how splash damage
 * works today. Polish-bag follow-up if "near-miss feels weak" feedback
 * surfaces later.
 *
 * <p><b>Unit conversions:</b>
 * <ul>
 *   <li>{@link RepelDistance} — Subspace pixels; converted ÷ 16 to world
 *       units (canonical 16 px/tile rate) at scan time.
 *   <li>{@link RepelSpeed} — Subspace velocity units; converted via the
 *       package-private
 *       {@link WeaponsSystem#effectiveProjectileSpeed} helper for unit-
 *       bridge consistency with {@code BombSpeed} / {@code BulletSpeed} /
 *       {@code BurstSpeed} / {@code BombThrust}.
 * </ul>
 *
 * <p><b>Mass-driven δ-velocity:</b> the impulse magnitude is the same
 * for every body in radius, but mphys turns impulse → δ-velocity by
 * dividing by {@code Mass}. Ships (canonically heavier) budge less,
 * bombs (canonically lighter) reverse hard — automatic mass-aware
 * "weight" without an explicit weight field on {@code Repellable}.
 *
 * <p>b2-phased scope (Q2.2): only ships and bombs carry {@code Repellable}
 * today (per {@link infinity.config.ShipConfig#repellable} and
 * {@link infinity.config.BombConfig#repellable}). Bullets / bursts /
 * mines / gravbombs / thors deferred to a follow-up — polish-bag entry.
 *
 * @author Asser
 */
public class RepelSystem extends AbstractGameSystem {

  /**
   * Subspace pixels-per-tile rate (canonical 16 px/tile). Local constant
   * because no shared engine-tier {@code px/tile} value exists yet —
   * polish-bag candidate to centralize when the second consumer of this
   * conversion appears.
   */
  private static final double PIXELS_PER_TILE = 16.0;

  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private ArenaSystem arenaSystem;
  private EngineConfigSystem engineConfigSystem;
  private EntitySet repelEffects;
  private EntitySet repellables;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    if (ed == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires an EntityData object.");
    }
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> phys = getSystem(MPhysSystem.class);
    if (phys == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the MPhysSystem system.");
    }
    physicsSpace = phys.getPhysicsSpace();
    arenaSystem = getSystem(ArenaSystem.class);
    if (arenaSystem == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the ArenaSystem (zone config).");
    }
    engineConfigSystem = getSystem(EngineConfigSystem.class);
    if (engineConfigSystem == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the EngineConfigSystem.");
    }
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

  /**
   * One-shot scan for a single repel effect entity. Reads the effect's
   * world position via {@link PhysicsSpace}, walks all {@link Repellable}
   * bodies, distance-filters by {@code RepelDistance / 16}, applies an
   * {@link Impulse} away from the effect center to those in range. Skips
   * the firing ship (identified by {@link Parent} on the effect) and —
   * when zone-level {@code repelFriendlies} is false — same-frequency
   * ships.
   */
  private void applyRepelImpulse(
      final Entity effect,
      final double scale,
      final double maxJme,
      final boolean repelFriendlies) {
    final EntityId effectId = effect.getId();
    final RigidBody<EntityId, MBlockShape> effectBody =
        physicsSpace.getBinIndex().getRigidBody(effectId);
    if (effectBody == null) {
      return; // body not yet bound; can't compute direction
    }
    final Vec3d effectPos = effectBody.position;

    final EntityId ownerId = effect.get(Parent.class).getParentEntityId();
    final Frequency ownerFreq =
        ownerId == null ? null : ed.getComponent(ownerId, Frequency.class);
    final Integer ownerFreqValue = ownerFreq == null ? null : ownerFreq.getFrequency();

    final double radiusWorldUnits =
        effect.get(RepelDistance.class).getPixels() / PIXELS_PER_TILE;
    if (radiusWorldUnits <= 0.0) {
      return;
    }
    final double radiusSq = radiusWorldUnits * radiusWorldUnits;

    final double magnitudeJme =
        WeaponsSystem.effectiveProjectileSpeed(
            effect.get(RepelSpeed.class).getSpeed(), scale, maxJme);
    if (magnitudeJme == 0.0) {
      return;
    }

    for (final Entity victim : repellables) {
      final EntityId victimId = victim.getId();
      if (victimId.equals(ownerId)) {
        continue; // self-repel filter (Q3)
      }
      if (!repelFriendlies) {
        final Frequency victimFreq = ed.getComponent(victimId, Frequency.class);
        final Integer victimFreqValue =
            victimFreq == null ? null : victimFreq.getFrequency();
        // Same-team ships skipped only when both have a freq AND the freqs
        // match; entities without Frequency (bombs / mines / debris) are
        // always pushed regardless of the toggle.
        if (ownerFreqValue != null
            && victimFreqValue != null
            && ownerFreqValue.equals(victimFreqValue)) {
          continue;
        }
      }
      final RigidBody<EntityId, MBlockShape> victimBody =
          physicsSpace.getBinIndex().getRigidBody(victimId);
      if (victimBody == null) {
        continue;
      }
      final Vec3d vp = victimBody.position;
      final double dx = vp.x - effectPos.x;
      final double dz = vp.z - effectPos.z; // Q8: planar (X, Z); Y always 0
      final double distSq = dx * dx + dz * dz;
      if (distSq > radiusSq) {
        continue;
      }
      if (distSq <= 0.0) {
        continue; // coincident with effect center; direction undefined
      }
      final double dist = Math.sqrt(distSq);
      final Vec3d impulse =
          new Vec3d((dx / dist) * magnitudeJme, 0.0, (dz / dist) * magnitudeJme);
      ed.setComponent(victimId, new Impulse(impulse));
    }
  }

  @Override
  public void start() {
    return;
  }

  @Override
  public void stop() {
    return;
  }
}
