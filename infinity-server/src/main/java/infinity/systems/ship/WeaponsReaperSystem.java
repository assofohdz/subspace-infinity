// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.es.Damage;
import infinity.es.SplashDamage;
import infinity.es.ship.Energy;
import infinity.settings.ConfigRegistrySystem;
import infinity.sim.WeaponFactory;
import infinity.systems.ArenaSystem;
import infinity.systems.BaseInfinitySystem;

/**
 * Replacement-as-Mutation pilot — reaper-side carve from the legacy
 * {@code WeaponsSystem} god-class. Owns the post-detonation cleanup pipeline:
 * splash-damage scan, direct-hit damage emission, explosion-entity spawn, and
 * the end-of-life {@link Decay} stamp on the spent projectile.
 *
 * <p>RaM canonical-writer ledger for this system:
 * <ul>
 *   <li><b>Projectile end-of-life</b> (single writer): stamps
 *       {@link Decay#duration(long, long) Decay.duration(now, 0)} on a
 *       detonated projectile so the central decay reaper sweeps it next tick.
 *       Per {@code .claude/rules/decay-ttl.md}, the central reaper is the
 *       only entity-removal site; this system owns the deadline-stamp side
 *       of that contract for projectiles.
 *   <li><b>Spawn-time projection of explosion entities</b> (RaM-OK): each
 *       detonation creates a fresh visual / audio explosion entity via
 *       {@link WeaponFactory#createExplosion}.
 *   <li><b>Damage intent emission</b> (RaM-correct, drained by
 *       {@link EnergySystem}): direct-hit and splash damage paths route
 *       through {@link WeaponsDamageLogic#applyDirectHitDamage} /
 *       {@link WeaponsDamageLogic#applySplashDamage}, which call the
 *       attributed {@link EnergySystem#damage(EntityId, int, EntityId, byte)}
 *       overload — the canonical {@link infinity.es.ship.EnergyChange}
 *       writer still owns the {@link Energy} mutation.
 *   <li><b>{@link infinity.es.Jitter} stamp</b> (single writer for the
 *       splash + direct-hit damage paths via
 *       {@link WeaponsDamageLogic#stampJitter}). Mirrors the legacy seam;
 *       a follow-up slice may convert to a {@code JitterIntent}.
 * </ul>
 *
 * <p>Public API: {@link #detonate(EntityId, Damage, Vec3d, EntityId, long)} —
 * the single seam used by {@link WeaponsImpactSystem} (collision-driven
 * detonation) and {@link ProximityFuseSystem} (fuse-elapsed detonation). This
 * mirrors the existing {@link EnergySystem#damage(EntityId, int)} shape: a
 * public method other systems call in-tick rather than emitting a separate
 * {@code DetonationIntent} entity. The simpler shape was chosen for the
 * pilot — converting to a true async intent queue is a follow-up slice if
 * determinism testing demands it.
 *
 * @author AFahrenholz
 */
public class WeaponsReaperSystem extends BaseInfinitySystem {

  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private ConfigRegistrySystem configRegistry;
  private ArenaSystem arenaSystem;
  private EnergySystem energySystem;

  /**
   * Energy-bearer EntitySet — the splash-damage scan filter. Held by this
   * system so {@link WeaponsImpactSystem} doesn't duplicate the same set; the
   * splash path needs a snapshot of the live {@link Energy} bearers to gate
   * out non-ship hits (projectiles, prizes, doors) before damage.
   */
  private EntitySet healthBearers;

  @Override
  @SuppressWarnings("unchecked")
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    final MPhysSystem<MBlockShape> physics = requireSystem(MPhysSystem.class);
    physicsSpace = physics.getPhysicsSpace();
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    arenaSystem = requireSystem(ArenaSystem.class);
    energySystem = requireSystem(EnergySystem.class);

    healthBearers = ed.getEntities(Energy.class);
  }

  @Override
  protected void terminate() {
    healthBearers.release();
    healthBearers = null;
  }

  @Override
  public void update(final SimTime tpf) {
    // Keep the splash-scan filter fresh.
    healthBearers.applyChanges();
  }

  /**
   * Public entry point for both the contact path
   * ({@link WeaponsImpactSystem#newContact}) and the proximity-fuse path
   * ({@link ProximityFuseSystem}). Applies damage (splash if
   * {@link SplashDamage} is present, direct-hit otherwise), spawns the visual
   * explosion entity, and stamps {@code Decay(now, 0)} so the central decay
   * reaper sweeps the projectile next tick.
   *
   * @param damageEntityId the projectile entity that's detonating
   * @param damage the projectile's {@link Damage} component (already resolved
   *     by the caller)
   * @param explosionPoint the world-space detonation point — for contact
   *     detonation this is the contact point; for proximity-fuse detonation
   *     this is the projectile's body position
   * @param directVictimId the entity that triggered a direct-contact
   *     detonation, or {@code null} for wall-hit / proximity-fuse paths where
   *     no single victim exists. Splash bombs ignore this argument and damage
   *     everything in {@link SplashDamage#getRadiusWorldUnits()}.
   * @param nowSimNanos current simulation time — passed in (rather than read
   *     from this system's own ticker) so callers in other systems don't
   *     depend on update-order timing.
   */
  public void detonate(
      final EntityId damageEntityId,
      final Damage damage,
      final Vec3d explosionPoint,
      final EntityId directVictimId,
      final long nowSimNanos) {
    final SplashDamage splash = ed.getComponent(damageEntityId, SplashDamage.class);
    if (splash != null) {
      WeaponsDamageLogic.applySplashDamage(
          ed,
          healthBearers,
          physicsSpace,
          arenaSystem,
          configRegistry,
          energySystem,
          damageEntityId,
          damage,
          splash,
          explosionPoint,
          nowSimNanos);
    } else if (directVictimId != null) {
      WeaponsDamageLogic.applyDirectHitDamage(
          ed,
          arenaSystem,
          configRegistry,
          energySystem,
          damageEntityId,
          damage,
          directVictimId,
          nowSimNanos);
    }
    WeaponFactory.createExplosion(
        ed,
        new infinity.sim.specs.ExplosionSpec(
            EntityId.NULL_ID,
            physicsSpace,
            nowSimNanos,
            explosionPoint,
            damage.getExplosionDecay(),
            damage.getExplosionShape()));
    ed.setComponent(damageEntityId, Decay.duration(nowSimNanos, 0));
  }
}
