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
public class RepelSystem extends BaseInfinitySystem {

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

  /**
   * One-shot scan for a single repel effect entity. Reads the effect's
   * world position via {@link PhysicsSpace}, walks all {@link Repellable}
   * bodies, distance-filters by {@code RepelDistance / 16}, applies an
   * {@link Impulse} away from the effect center to those in range. Skips
   * the firing ship (identified by {@link Parent} on the effect) and —
   * when zone-level {@code repelFriendlies} is false — same-frequency
   * ships.
   *
   * <p>Per-victim gating split into the static pure functions
   * {@link #shouldRepelVictim} (self / FF gate) and
   * {@link #planarImpulse} (distance + direction) so the gating logic is
   * unit-testable without bringing up an ECS / physics fixture (mirrors
   * {@link ProximityFuseSystem#shouldArmOn}).
   */
  private void applyRepelImpulse(
      final Entity effect,
      final double scale,
      final double maxJme,
      final boolean repelFriendlies) {
    final RigidBody<EntityId, MBlockShape> effectBody =
        physicsSpace.getBinIndex().getRigidBody(effect.getId());
    if (effectBody == null) {
      return; // body not yet bound; can't compute direction
    }
    final double radiusWorldUnits =
        effect.get(RepelDistance.class).getPixels() / PIXELS_PER_TILE;
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

  /**
   * Per-victim attempt: gate, distance check, stamp impulse if all pass.
   * Extracted from {@link #applyRepelImpulse} to keep that method below the
   * complexity threshold; the gating decisions live in static pure helpers.
   */
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

  /**
   * Pure-function FF + self-repel gate. {@code true} when this victim
   * should be repelled by an effect owned by {@code ownerId}.
   *
   * <p>Self-repel filter (Q3): victim == owner returns false. FF gate
   * (Q4): when {@code repelFriendlies} is true, every other victim is
   * pushed; when false, same-frequency ships are skipped (entities
   * without {@link Frequency} are always pushed regardless of the
   * toggle).
   *
   * <p>Package-private so unit tests can pin the tri-state without
   * standing up an ECS fixture.
   */
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

  /**
   * Pure-function planar impulse computation. Returns the impulse vector
   * (Y=0) for a victim at offset {@code (dx, dz)} from the effect center,
   * or {@code null} if the victim is outside {@code radiusSq} or
   * coincident with the center (direction undefined). Magnitude is the
   * pre-computed jME-scale impulse magnitude.
   *
   * <p>Package-private so unit tests can pin the radius / falloff /
   * direction rules without bringing up a physics fixture.
   */
  static Vec3d planarImpulse(
      final double dx, final double dz, final double magnitudeJme, final double radiusSq) {
    final double distSq = dx * dx + dz * dz;
    if (distSq > radiusSq || distSq <= 0.0) {
      return null;
    }
    final double dist = Math.sqrt(distSq);
    return new Vec3d((dx / dist) * magnitudeJme, 0.0, (dz / dist) * magnitudeJme);
  }

  private Integer freqValue(final EntityId id) {
    if (id == null) {
      return null;
    }
    final Frequency freq = ed.getComponent(id, Frequency.class);
    return freq == null ? null : freq.getFrequency();
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
