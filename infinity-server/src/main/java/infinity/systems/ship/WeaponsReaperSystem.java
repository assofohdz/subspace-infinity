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
import infinity.sim.Detonator;
import infinity.sim.WeaponFactory;
import infinity.systems.BaseInfinitySystem;

/** Post-detonation cleanup — splash + direct-hit damage, explosion spawn, projectile end-of-life {@link Decay} stamp. */
public class WeaponsReaperSystem extends BaseInfinitySystem implements Detonator {

  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private EnergySystem energySystem;

  private EntitySet healthBearers;

  @Override
  @SuppressWarnings("unchecked")
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    final MPhysSystem<MBlockShape> physics = requireSystem(MPhysSystem.class);
    physicsSpace = physics.getPhysicsSpace();
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
    healthBearers.applyChanges();
  }

  /** Detonation seam called by {@link WeaponsImpactSystem} (contact) and {@link ProximityFuseSystem} (fuse). */
  @Override
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
          energySystem,
          damageEntityId,
          damage,
          splash,
          explosionPoint,
          nowSimNanos);
    } else if (directVictimId != null) {
      WeaponsDamageLogic.applyDirectHitDamage(
          ed,
          energySystem,
          damageEntityId,
          damage,
          directVictimId,
          nowSimNanos);
    }
    WeaponFactory.createExplosion(
        ed,
        new infinity.sim.specs.ExplosionArgs(
            EntityId.NULL_ID,
            physicsSpace,
            nowSimNanos,
            explosionPoint,
            damage.getExplosionDecay(),
            damage.getExplosionShape()));
    ed.setComponent(damageEntityId, Decay.duration(nowSimNanos, 0));
  }
}
