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

/**
 * Damage-application + collision-decision helpers extracted from
 * {@link WeaponsSystem} so the giant weapons class stays under PMD's
 * class-level cyclomatic-complexity ceiling.
 *
 * <p>Every method here is {@code static} package-private. Each one receives
 * exactly the dependencies it needs as explicit arguments — there's no shared
 * "context" object. WeaponsSystem's instance methods become thin delegators
 * that capture {@code this.ed}, {@code this.physicsSpace}, etc. and forward.
 *
 * <p>Coordinates with {@link WeaponsLogic} (round 19) — that class already
 * holds the projectile-velocity math (incl. the {@link WeaponsLogic#recoilImpulse}
 * pure-function recoil computation). This class wraps {@code recoilImpulse} in
 * the ECS / physics fetch + Impulse-stamp boilerplate, and adds the damage
 * + splash + jitter + FF-decision pipeline.
 *
 * <p>Behaviour-preservation contract: every helper produces identical outputs
 * to the original WeaponsSystem method. WeaponsSystem.newContact runs every
 * collision frame, so any divergence would manifest as a visible-gameplay
 * regression. The split is mechanical — same call order, same branch
 * semantics, same component access pattern.
 */
final class WeaponsDamageLogic {

    private WeaponsDamageLogic() {
        // utility class — instantiation prevented
    }

    /**
     * Direct-hit (non-splash) damage path. Subject to the arena's friendly-fire
     * mode: only mode 2 ("all friendly fire") allows same-team damage; modes 0
     * and 1 swallow the damage but the projectile still detonates (for visual
     * feedback / consumption).
     *
     * <p>Replacement-as-Mutation slice 1: the {@code energy.damage} call now
     * uses the attributed overload, threading the attacker's ship id (resolved
     * via {@code Parent} on {@code damageEntityId}, with {@link EntityId#NULL_ID}
     * fallback for orphan projectiles) onto a {@link infinity.es.DamageSource}
     * sibling on the intent entity. Per-weapon-family attribution is a
     * follow-up slice — today's damage path passes {@link WeaponType#NONE}.
     * See the deferred-TODO note in the system file.
     */
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

    /**
     * Splash (AoE) damage path. Spatial pre-filter via
     * {@code mphys.PhysicsSpace#queryBounds} (arch-review TD-3 — replaces the
     * per-tick O(N) walk over every Health-bearer) returns rigid bodies whose
     * bounds intersect the splash sphere. The post-query loop retains those
     * within the strict {@code splash.radiusWorldUnits} of the detonation
     * point, gates them against the {@code energyEntities} EntitySet (only
     * Health-bearers count for damage; queryBounds also returns projectiles,
     * prizes, doors etc.), and applies {@code damage.intendedDamage} subject
     * to the friendly-fire gate (splash uses the relaxed "mode &gt;= 1" rule).
     * Does not yet attenuate damage with distance — that's a polish-bag
     * follow-up.
     */
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
                continue; // not a Health-bearer (projectile, prize, door, …)
            }
            final Vec3d vp = body.position;
            final double dx = vp.x - explosionPoint.x;
            final double dy = vp.y - explosionPoint.y;
            final double dz = vp.z - explosionPoint.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                // Strict point-distance check; queryBounds inflates by
                // body.boundsRadius so it's a coarse pre-filter only.
                continue;
            }
            if (!shouldDamageVictim(ed, arenaSys, damageEntityId, victimId, true)) {
                continue;
            }
            energy.damage(victimId, damage.getIntendedDamage(), attackerShipId, WeaponType.NONE);
            stampJitter(ed, cr, damageEntityId, victimId, nowSimNanos);
        }
    }

    /**
     * Stamps a {@link Jitter} component on a bomb-damage victim after the FF
     * gate has passed. Resolves the firing arena's
     * {@link infinity.config.BombConfig#jitterTimeMs} from the bomb's
     * {@link Parent}; no-ops if the value is 0 (arena disabled), if the bomb
     * has no parent (no attacker context), or if the existing {@code Jitter}
     * already runs longer than what this hit would extend to (Q4=a:
     * max(existing, new), never shorten an in-flight shake).
     */
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

    /**
     * Friendly-fire gate. Returns {@code true} if the damage-bearing entity
     * should be allowed to damage {@code victimId}, given the arena's
     * {@code friendlyFire} mode and whether this is a splash (AoE) hit.
     *
     * <ul>
     *   <li>If attacker or victim has no {@link Frequency} (NPC, prize, debris),
     *       no FF gate applies and damage goes through.
     *   <li>If teams differ, damage goes through (true enemy hit).
     *   <li>If teams match: mode 0 swallows; mode 1 allows splash but not direct
     *       hits; mode 2 allows everything.
     * </ul>
     *
     * <p>Bottoms out on {@link WeaponsLogic#shouldDamageVictim} for the pure
     * tri-state mode decision; this overload handles the ECS-side parent /
     * frequency / arena-mode lookups.
     */
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
        if (attackerShipId.equals(victimId)) {
            // Self-damage already filtered by ContactSystem.parentChildContact;
            // guard here is a defence-in-depth no-op for the splash scan.
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

    /**
     * Slice S2 — apply per-ship recoil ({@link BombStats#thrust()}) as an
     * {@link Impulse} on the firing ship. Direction = opposite the ship's
     * forward in world space (Subspace canon: "back-thrust on fire" applies
     * directly behind the ship regardless of the bomb's outgoing velocity).
     *
     * <p>Auto-no-op when the ship has no {@link BombStats} (not equipped),
     * thrust is 0, or the body isn't yet bound to the entity.
     */
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

    /**
     * Resolve the friendly-fire mode for the arena that owns {@code attackerShipId}.
     * Falls back to {@link ArenaConfig#EMPTY}'s default ({@code 0} = off) when
     * the attacker has no {@link ArenaId} (no-arena void / spawner-fired).
     */
    static int friendlyFireModeFor(
            final EntityData ed, final ArenaSystem arenaSys, final EntityId attackerShipId) {
        final ArenaId arenaId = ed.getComponent(attackerShipId, ArenaId.class);
        if (arenaId == null) {
            return ArenaConfig.EMPTY.friendlyFire();
        }
        return arenaSys.getArenaConfig(arenaId.getArena()).friendlyFire();
    }

    /**
     * Per-arena config lookup mirroring {@code WeaponsFireSystem.weaponsFor}:
     * the attacker's {@link ArenaId} keys into {@link ConfigRegistrySystem};
     * arenas with no config get {@link ConfigRegistry#EMPTY}. Falls back to
     * {@code EMPTY} when the attacker has no {@code ArenaId} (no-arena void).
     */
    private static ConfigRegistry weaponsFor(
            final EntityData ed, final ConfigRegistrySystem cr, final EntityId attacker) {
        final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
        if (arenaId == null) {
            return ConfigRegistry.EMPTY;
        }
        return cr.forArena(arenaId);
    }

    /**
     * Resolve the firing ship's {@link EntityId} from a projectile/damage entity
     * via its {@link Parent}. Returns {@link EntityId#NULL_ID} when the projectile
     * has no parent (orphan / wall-hit / synthetic detonation entity) so the
     * attributed {@code energy.damage(...)} call always has a non-null
     * {@code source} value to record on {@link infinity.es.DamageSource}.
     */
    static EntityId attackerShipIdOf(final EntityData ed, final EntityId damageEntityId) {
        final Parent parent = ed.getComponent(damageEntityId, Parent.class);
        if (parent == null) {
            return EntityId.NULL_ID;
        }
        final EntityId attackerShipId = parent.getParentEntityId();
        return attackerShipId == null ? EntityId.NULL_ID : attackerShipId;
    }
}
