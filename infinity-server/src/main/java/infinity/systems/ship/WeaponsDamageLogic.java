// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.Impulse;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.QueryFilter;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.SphereVolume;
import infinity.config.ArenaConfig;
import infinity.config.EngineConfig;
import infinity.es.Damage;
import infinity.es.Frequency;
import infinity.es.Jitter;
import infinity.es.Parent;
import infinity.es.SplashDamage;
import infinity.es.arena.ArenaId;
import infinity.es.ship.weapons.BombStats;
import infinity.es.ship.weapons.WeaponType;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import infinity.systems.ArenaSystem;

/** Damage / splash / jitter / FF helpers; companion to {@link WeaponsLogic} (pure math) and {@link WeaponsFireSystem} (ECS). */
final class WeaponsDamageLogic {

    private WeaponsDamageLogic() {}

    /** Direct-hit damage; FF mode 2 allows same-team, modes 0/1 swallow damage but still detonate. */
    static void applyDirectHitDamage(
            final EntityData ed,
            final ArenaSystem arenaSys,
            final ConfigRegistrySystem cr,
            final EnergySystem energy,
            final EntityId damageEntityId,
            final Damage damage,
            final EntityId victimId,
            final long nowSimNanos) {
        if (!shouldDamageVictim(ed, arenaSys, damageEntityId, victimId, false)) {
            return;
        }
        final EntityId attackerShipId = attackerShipIdOf(ed, damageEntityId);
        energy.damage(victimId, damage.getIntendedDamage(), attackerShipId, WeaponType.NONE);
        stampJitter(ed, cr, damageEntityId, victimId, nowSimNanos);
    }

    /** Splash damage via {@code physicsSpace.queryBounds} pre-filter + strict distance check; splash FF uses mode≥1. */
    static void applySplashDamage(
            final EntityData ed,
            final EntitySet energyEntities,
            final PhysicsSpace<EntityId, MBlockShape> physicsSpace,
            final ArenaSystem arenaSys,
            final ConfigRegistrySystem cr,
            final EnergySystem energy,
            final EntityId damageEntityId,
            final Damage damage,
            final SplashDamage splash,
            final Vec3d explosionPoint,
            final long nowSimNanos) {
        final double radius = splash.getRadiusWorldUnits();
        if (radius <= 0.0) {
            return;
        }
        final double radiusSq = radius * radius;
        final EntityId attackerShipId = attackerShipIdOf(ed, damageEntityId);
        final SphereVolume sphere = new SphereVolume(explosionPoint, radius);
        final QueryFilter<EntityId, MBlockShape> filter =
                new QueryFilter<>(
                        QueryFilter.TYPE_ACTIVE | QueryFilter.TYPE_INACTIVE,
                        body -> true,
                        body -> true);
        for (final AbstractBody<EntityId, MBlockShape> body : physicsSpace.queryBounds(sphere, filter)) {
            final EntityId victimId = body.id;
            if (!energyEntities.containsId(victimId)) {
                continue;
            }
            final Vec3d vp = body.position;
            final double dx = vp.x - explosionPoint.x;
            final double dy = vp.y - explosionPoint.y;
            final double dz = vp.z - explosionPoint.z;
            // queryBounds inflates by body.boundsRadius — re-check strict point distance.
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                continue;
            }
            if (!shouldDamageVictim(ed, arenaSys, damageEntityId, victimId, true)) {
                continue;
            }
            energy.damage(victimId, damage.getIntendedDamage(), attackerShipId, WeaponType.NONE);
            stampJitter(ed, cr, damageEntityId, victimId, nowSimNanos);
        }
    }

    /** Stamps {@link Jitter} on bomb-hit victims; takes max(existing, new) to never shorten an in-flight shake. */
    static void stampJitter(
            final EntityData ed,
            final ConfigRegistrySystem cr,
            final EntityId damageEntityId,
            final EntityId victimId,
            final long nowSimNanos) {
        final Parent parent = ed.getComponent(damageEntityId, Parent.class);
        if (parent == null) {
            return;
        }
        final EntityId attackerShipId = parent.getParentEntityId();
        if (attackerShipId == null) {
            return;
        }
        final long jitterMs = weaponsFor(ed, cr, attackerShipId).bomb().jitterTimeMs();
        if (jitterMs <= 0L) {
            return;
        }
        final long newEnd = nowSimNanos + jitterMs * 1_000_000L;
        final Jitter existing = ed.getComponent(victimId, Jitter.class);
        if (existing == null || newEnd > existing.getEndTime()) {
            ed.setComponent(victimId, new Jitter(nowSimNanos, newEnd));
        }
    }

    /** FF gate; bottoms out on {@link WeaponsLogic#shouldDamageVictim} for the pure tri-state decision. */
    static boolean shouldDamageVictim(
            final EntityData ed,
            final ArenaSystem arenaSys,
            final EntityId damageEntityId,
            final EntityId victimId,
            final boolean isSplash) {
        final Parent parent = ed.getComponent(damageEntityId, Parent.class);
        if (parent == null) {
            return true;
        }
        final EntityId attackerShipId = parent.getParentEntityId();
        if (attackerShipId == null) {
            return true;
        }
        // Splash-scan defence-in-depth; direct self-damage already filtered by ContactSystem.parentChildContact.
        if (attackerShipId.equals(victimId)) {
            return false;
        }
        final Frequency attackerFreq = ed.getComponent(attackerShipId, Frequency.class);
        final Frequency victimFreq = ed.getComponent(victimId, Frequency.class);
        final Integer attackerFreqValue =
                attackerFreq == null ? null : attackerFreq.getFrequency();
        final Integer victimFreqValue = victimFreq == null ? null : victimFreq.getFrequency();
        final int ffMode = friendlyFireModeFor(ed, arenaSys, attackerShipId);
        return WeaponsLogic.shouldDamageVictim(attackerFreqValue, victimFreqValue, ffMode, isSplash);
    }

    /** Apply per-ship {@link BombStats#thrust()} recoil as {@link Impulse}; direction = opposite ship forward. */
    static void applyBombRecoil(
            final EntityData ed,
            final PhysicsSpace<EntityId, MBlockShape> physicsSpace,
            final EngineConfigSystem engineConfigSystem,
            final EntityId shipId) {
        final BombStats stats = ed.getComponent(shipId, BombStats.class);
        if (stats == null || stats.thrust() == 0) {
            return;
        }
        final RigidBody<?, ?> shipBody =
                physicsSpace.getBinIndex().getRigidBody(shipId);
        if (shipBody == null) {
            return;
        }
        final EngineConfig engineCfg = engineConfigSystem.get();
        final Vec3d impulse =
                WeaponsLogic.recoilImpulse(
                        stats.thrust(),
                        engineCfg.bombThrustScale(),
                        engineCfg.maxProjectileSpeedJme(),
                        new Quatd(shipBody.orientation));
        ed.setComponent(shipId, new Impulse(impulse));
    }

    static int friendlyFireModeFor(
            final EntityData ed, final ArenaSystem arenaSys, final EntityId attackerShipId) {
        final ArenaId arenaId = ed.getComponent(attackerShipId, ArenaId.class);
        if (arenaId == null) {
            return ArenaConfig.EMPTY.friendlyFire();
        }
        return arenaSys.getArenaConfig(arenaId.getArena()).friendlyFire();
    }

    private static ConfigRegistry weaponsFor(
            final EntityData ed, final ConfigRegistrySystem cr, final EntityId attacker) {
        final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
        if (arenaId == null) {
            return ConfigRegistry.EMPTY;
        }
        return cr.forArena(arenaId);
    }

    /** Resolves firing ship via {@link Parent}; {@link EntityId#NULL_ID} for orphan projectiles. */
    static EntityId attackerShipIdOf(final EntityData ed, final EntityId damageEntityId) {
        final Parent parent = ed.getComponent(damageEntityId, Parent.class);
        if (parent == null) {
            return EntityId.NULL_ID;
        }
        final EntityId attackerShipId = parent.getParentEntityId();
        return attackerShipId == null ? EntityId.NULL_ID : attackerShipId;
    }
}
