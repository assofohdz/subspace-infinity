// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.Impulse;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import infinity.config.ArenaConfig;
import infinity.config.BombConfig;
import infinity.config.EngineConfig;
import infinity.es.Damage;
import infinity.es.Frequency;
import infinity.es.Jitter;
import infinity.es.Parent;
import infinity.es.SplashDamage;
import infinity.es.arena.ArenaId;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombThrust;
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
        energy.damage(victimId, damage.getIntendedDamage());
        stampJitter(ed, cr, damageEntityId, victimId, nowSimNanos);
    }

    /**
     * Splash (AoE) damage path. Iterates {@code energyEntities}, retains those
     * within {@code splash.radiusWorldUnits} of the detonation point, and
     * applies {@code damage.intendedDamage} to each (subject to the
     * friendly-fire gate — splash uses the relaxed "mode &gt;= 1" rule). Does
     * not yet attenuate damage with distance — that's a polish-bag follow-up.
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
        for (final Entity victim : energyEntities) {
            final EntityId victimId = victim.getId();
            final RigidBody<EntityId, MBlockShape> body =
                    physicsSpace.getBinIndex().getRigidBody(victimId);
            if (body == null) {
                continue;
            }
            final Vec3d vp = body.position;
            final double dx = vp.x - explosionPoint.x;
            final double dy = vp.y - explosionPoint.y;
            final double dz = vp.z - explosionPoint.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                continue;
            }
            if (!shouldDamageVictim(ed, arenaSys, damageEntityId, victimId, true)) {
                continue;
            }
            energy.damage(victimId, damage.getIntendedDamage());
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
     * Slice S2 — apply per-ship {@code BombThrust} recoil as an
     * {@link Impulse} on the firing ship. Direction = opposite the ship's
     * forward in world space (Subspace canon: "back-thrust on fire" applies
     * directly behind the ship regardless of the bomb's outgoing velocity,
     * which would include ship-velocity inheritance).
     *
     * <p>Magnitude reuses {@link WeaponsLogic#recoilImpulse} so the engine-tier
     * {@code bombThrustScale} and {@code maxProjectileSpeedJme} cap apply
     * uniformly. Sign-preserving (negative thrust = forward push).
     *
     * <p>Auto-no-op when the ship has no {@code BombThrust} component, the
     * value is 0, or the body isn't yet bound to the entity (sio2-mphys
     * {@code Impulse} retries until body binds).
     */
    static void applyBombRecoil(
            final EntityData ed,
            final PhysicsSpace<EntityId, MBlockShape> physicsSpace,
            final EngineConfigSystem engineConfigSystem,
            final EntityId shipId) {
        final BombThrust thrust = ed.getComponent(shipId, BombThrust.class);
        if (thrust == null || thrust.getThrust() == 0) {
            return;
        }
        final RigidBody<?, ?> shipBody =
                physicsSpace.getBinIndex().getRigidBody(shipId);
        if (shipBody == null) {
            return;
        }
        final EngineConfig engineCfg = engineConfigSystem.get();
        // Slice S2-cal — recoil uses its own engine-tier `bombThrustScale`,
        // distinct from `subspaceVelocityScale` used by projectile-speed
        // paths. The projectile fit (400 × 0.01 = 4.0) felt too pushy in
        // S2 playtest. Default `bombThrustScale 0.0005` lands SVS canon
        // BombThrust 400 at 0.2 jME/sec backward impulse — a subtle nudge
        // (~1% of ship max-speed). Cap reuses `maxProjectileSpeedJme` for
        // physics-safety.
        final Vec3d impulse =
                WeaponsLogic.recoilImpulse(
                        thrust.getThrust(),
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
     * Slice 9c-BombSafety — fire-time gate that rejects bomb fire when an
     * enemy {@link infinity.es.ship.Health}-bearer sits inside the firing
     * ship's effective proximity-arm radius. Auto-no-ops when:
     *
     * <ul>
     *   <li>The arena's {@code BombConfig.bombSafety} is {@code false}
     *       (operator opt-in).
     *   <li>The arena's {@code BombConfig.proximityDistance} is {@code 0} —
     *       proximity disabled means the ship's bomb wouldn't proximity-arm
     *       on anything anyway, so "inside the arming radius" has no
     *       meaning.
     *   <li>The firing ship has no rigid body in the physics space (mid-spawn
     *       / dead).
     * </ul>
     *
     * <p>FF gate reused from {@link WeaponsLogic#victimBlocksBombFire} —
     * same-team ships never arm proximity bombs, so they don't count for
     * the safety scan either. Friendlies hugging you don't block fire.
     */
    static boolean bombSafetyClear(
            final EntityData ed,
            final ConfigRegistrySystem cr,
            final PhysicsSpace<EntityId, MBlockShape> physicsSpace,
            final EntitySet bombs,
            final EntitySet energyEntities,
            final EntityId requesterId) {
        final RigidBody<EntityId, MBlockShape> ownerBody =
                physicsSpace.getBinIndex().getRigidBody(requesterId);
        if (ownerBody == null) {
            return true;
        }
        final double radius = effectiveBombSafetyRadius(ed, cr, bombs, requesterId);
        if (radius <= 0.0) {
            return true;
        }
        final Frequency ownerFreq = ed.getComponent(requesterId, Frequency.class);
        final Integer ownerFreqValue = ownerFreq == null ? null : ownerFreq.getFrequency();
        final Vec3d ownerPos = ownerBody.position;
        for (final Entity victim : energyEntities) {
            final EntityId victimId = victim.getId();
            if (victimId.equals(requesterId)) {
                continue;
            }
            final RigidBody<EntityId, MBlockShape> victimBody =
                    physicsSpace.getBinIndex().getRigidBody(victimId);
            if (victimBody == null) {
                continue;
            }
            final Frequency victimFreq = ed.getComponent(victimId, Frequency.class);
            final Integer victimFreqValue = victimFreq == null ? null : victimFreq.getFrequency();
            if (WeaponsLogic.victimBlocksBombFire(
                    ownerFreqValue, victimFreqValue, ownerPos, victimBody.position, radius)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the bomb-safety scan radius for {@code requesterId} after applying
     * the arena's BombConfig safety toggle and the per-bomb-level proximity
     * scaling. Returns {@code 0.0} when safety is off (clear-fire short-circuit)
     * so callers can early-out.
     */
    static double effectiveBombSafetyRadius(
            final EntityData ed,
            final ConfigRegistrySystem cr,
            final EntitySet bombs,
            final EntityId requesterId) {
        final ConfigRegistry cfg = weaponsFor(ed, cr, requesterId);
        final BombConfig bombCfg = cfg.bomb();
        if (!bombCfg.bombSafety() || bombCfg.proximityDistance() <= 0) {
            return 0.0;
        }
        final BombCurrentLevel bombLevel =
                bombs.getEntity(requesterId).get(BombCurrentLevel.class);
        return WeaponsLogic.proximityRadiusForLevel(
                bombCfg.proximityDistance(), bombLevel.getLevel().level);
    }

    /**
     * Per-arena config lookup mirroring {@code WeaponsSystem.weaponsFor}: the
     * attacker's {@link ArenaId} keys into {@link ConfigRegistrySystem};
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
}
