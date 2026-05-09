// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.QueryFilter;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.SphereVolume;
import infinity.config.BombConfig;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.ship.weapons.BombCost;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BulletCost;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.GravityBombCost;
import infinity.es.ship.weapons.GravityBombFireDelay;
import infinity.es.ship.weapons.MineCost;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;

/**
 * Pre-fire eligibility helpers extracted from {@link WeaponsSystem} so the
 * giant weapons class stays under PMD's class-level cyclomatic-complexity
 * ceiling. Every method here is {@code static} package-private — pure
 * decision logic that reads entity state + arena config and returns
 * {@code boolean}, with no side-effect mutation.
 *
 * <p>The {@code canAttackX} family answers "is this ship currently
 * permitted to fire weapon X?" — which combines four independent gates:
 *
 * <ol>
 *   <li>Inventory presence: ship's entity is in the matching weapon-type
 *       {@link EntitySet} (e.g. {@code bullets}).
 *   <li>Cooldown: matching {@code FireDelay} component's {@code getPercent()}
 *       is at 1.0 (decay-driven cooldown elapsed).
 *   <li>Energy: ship has enough {@code Health} to pay the matching
 *       {@code Cost} component.
 *   <li>Bomb-only: slice 9c-BombSafety scan rejects fire when an enemy
 *       sits inside the firing ship's effective proximity-arm radius.
 * </ol>
 *
 * <p>Bottoms out on {@link WeaponsLogic#victimBlocksBombFire} +
 * {@link WeaponsLogic#proximityRadiusForLevel} for the slice-9c bomb-safety
 * geometry math; this class wraps those with the EntitySet / EntityData
 * fetch boilerplate.
 *
 * <p>Behaviour-preservation contract: every helper produces identical
 * outputs to the original {@code WeaponsSystem.canAttackX} methods. The
 * split is mechanical — same call order, same branch semantics, same
 * component access pattern.
 */
final class WeaponsEligibility {

    private WeaponsEligibility() {
        // utility class — instantiation prevented
    }

    /**
     * Top-level dispatch — answer "can {@code requester} fire weapon
     * {@code weaponType} right now?" by routing to the per-weapon helper.
     * Null requester (entity not yet bound / mid-spawn) is rejected.
     */
    static boolean canAttack(
            final EntityData ed,
            final ConfigRegistrySystem cr,
            final PhysicsSpace<EntityId, MBlockShape> physicsSpace,
            final EnergySystem energy,
            final EntitySet bullets,
            final EntitySet bombs,
            final EntitySet gravityBombs,
            final EntitySet mines,
            final EntitySet bursts,
            final EntitySet energyEntities,
            final Entity requester,
            final byte weaponType) {
        if (requester == null) {
            return false;
        }
        switch (weaponType) {
            case WeaponsSystem.BULLET:
                return canAttackBullet(ed, bullets, energy, requester);
            case WeaponsSystem.BOMB:
                return canAttackBomb(ed, cr, physicsSpace, energy, bombs, energyEntities, requester);
            case WeaponsSystem.GRAVBOMB:
                return canAttackGravityBomb(ed, gravityBombs, energy, requester);
            case WeaponsSystem.MINE:
                return canAttackMine(ed, mines, energy, requester);
            case WeaponsSystem.BURST:
                return canAttackBurst(bursts, requester);
            default:
                return false;
        }
    }

    /** Bullet eligibility — inventory + cooldown + energy gates. */
    static boolean canAttackBullet(
            final EntityData ed,
            final EntitySet bullets,
            final EnergySystem energy,
            final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bullets.contains(requester)) {
            return false;
        }
        final BulletFireDelay gfd = ed.getComponent(requesterId, BulletFireDelay.class);
        if (gfd.getPercent() < 1) {
            return false;
        }
        final BulletCost gc = ed.getComponent(requesterId, BulletCost.class);
        return gc.getCost() <= energy.getHealth(requesterId);
    }

    /** Bomb eligibility — inventory + cooldown + energy + slice-9c safety scan. */
    static boolean canAttackBomb(
            final EntityData ed,
            final ConfigRegistrySystem cr,
            final PhysicsSpace<EntityId, MBlockShape> physicsSpace,
            final EnergySystem energy,
            final EntitySet bombs,
            final EntitySet energyEntities,
            final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bombs.contains(requester)) {
            return false;
        }
        final BombFireDelay bfd = ed.getComponent(requesterId, BombFireDelay.class);
        if (bfd.getPercent() < 1) {
            return false;
        }
        final BombCost bc = ed.getComponent(requesterId, BombCost.class);
        if (bc.getCost() > energy.getHealth(requesterId)) {
            return false;
        }
        return bombSafetyClear(ed, cr, physicsSpace, bombs, energyEntities, requesterId);
    }

    /** Gravity-bomb eligibility — inventory + cooldown + energy. */
    static boolean canAttackGravityBomb(
            final EntityData ed,
            final EntitySet gravityBombs,
            final EnergySystem energy,
            final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!gravityBombs.contains(requester)) {
            return false;
        }
        final GravityBombFireDelay bfd = ed.getComponent(requesterId, GravityBombFireDelay.class);
        if (bfd.getPercent() < 1) {
            return false;
        }
        final GravityBombCost bc = ed.getComponent(requesterId, GravityBombCost.class);
        return bc.getCost() <= energy.getHealth(requesterId);
    }

    /** Mine eligibility — inventory + cooldown + energy. */
    static boolean canAttackMine(
            final EntityData ed,
            final EntitySet mines,
            final EnergySystem energy,
            final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!mines.contains(requester)) {
            return false;
        }
        final MineFireDelay bfd = ed.getComponent(requesterId, MineFireDelay.class);
        if (bfd.getPercent() < 1) {
            return false;
        }
        final MineCost bc = ed.getComponent(requesterId, MineCost.class);
        return bc.getCost() <= energy.getHealth(requesterId);
    }

    /** Burst eligibility — inventory presence is the only gate (no cooldown / cost yet). */
    static boolean canAttackBurst(final EntitySet bursts, final Entity requester) {
        return bursts.contains(requester);
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
     *       on anything anyway.
     *   <li>The firing ship has no rigid body in the physics space (mid-spawn
     *       / dead).
     * </ul>
     *
     * <p>FF gate reused from {@link WeaponsLogic#victimBlocksBombFire} —
     * same-team ships never arm proximity bombs, so they don't count for
     * the safety scan either.
     *
     * <p>Spatial pre-filter via {@code mphys.PhysicsSpace#queryBounds}
     * (arch-review TD-3 — replaces the per-tick O(N) walk over every
     * Health-bearer with a bin-local active+inactive rigid-body scan).
     * The {@code energyEntities} set is preserved as the Health-bearer gate
     * post-query; the strict point-distance check inside
     * {@link WeaponsLogic#victimBlocksBombFire} preserves bit-exact radius
     * semantics (queryBounds inflates by {@code body.boundsRadius}).
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
        final SphereVolume sphere = new SphereVolume(ownerPos, radius);
        final QueryFilter<EntityId, MBlockShape> filter =
                new QueryFilter<>(
                        QueryFilter.TYPE_ACTIVE | QueryFilter.TYPE_INACTIVE,
                        body -> !body.id.equals(requesterId),
                        body -> true);
        for (final AbstractBody<EntityId, MBlockShape> body : physicsSpace.queryBounds(sphere, filter)) {
            final EntityId victimId = body.id;
            if (!energyEntities.containsId(victimId)) {
                continue; // not a Health-bearer (projectile, prize, door, …)
            }
            final Frequency victimFreq = ed.getComponent(victimId, Frequency.class);
            final Integer victimFreqValue = victimFreq == null ? null : victimFreq.getFrequency();
            if (WeaponsLogic.victimBlocksBombFire(
                    ownerFreqValue, victimFreqValue, ownerPos, body.position, radius)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the bomb-safety scan radius for {@code requesterId} after
     * applying the arena's BombConfig safety toggle and the per-bomb-level
     * proximity scaling. Returns {@code 0.0} when safety is off (clear-fire
     * short-circuit) so callers can early-out.
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

    // -----------------------------------------------------------------
    // Cooldown setters — stamp a fresh FireDelay component on the ship
    // -----------------------------------------------------------------

    /**
     * Top-level cooldown dispatch — answer "stamp the matching cooldown
     * component on {@code requester} for weapon {@code flag}". Returns
     * whether the cooldown was applied (false if the ship doesn't carry
     * the matching weapon-inventory component). Burst has no cooldown
     * yet — falls through to inventory presence only.
     */
    static boolean setCoolDown(
            final EntityData ed,
            final EntitySet bullets,
            final EntitySet bombs,
            final EntitySet gravityBombs,
            final EntitySet mines,
            final EntitySet bursts,
            final Entity requester,
            final byte flag) {
        if (requester == null) {
            return false;
        }
        if (flag == WeaponsSystem.BULLET) {
            return setCoolDownBullet(ed, bullets, requester);
        }
        if (flag == WeaponsSystem.BOMB) {
            return setCoolDownBomb(ed, bombs, requester);
        }
        if (flag == WeaponsSystem.GRAVBOMB) {
            return setCoolDownGravityBomb(ed, gravityBombs, requester);
        }
        if (flag == WeaponsSystem.MINE) {
            return setCoolDownMine(ed, mines, requester);
        }
        if (flag == WeaponsSystem.BURST) {
            // No delay on this for now
            return bursts.contains(requester);
        }
        return false;
    }

    /** Stamp a fresh {@code BulletFireDelay} on {@code requester}. */
    static boolean setCoolDownBullet(
            final EntityData ed, final EntitySet bullets, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bullets.contains(requester)) {
            return false;
        }
        final BulletFireDelay gfd = ed.getComponent(requesterId, BulletFireDelay.class);
        ed.setComponent(requesterId, gfd.copy());
        return true;
    }

    /** Stamp a fresh {@code BombFireDelay} on {@code requester}. */
    static boolean setCoolDownBomb(
            final EntityData ed, final EntitySet bombs, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bombs.contains(requester)) {
            return false;
        }
        final BombFireDelay bfd = ed.getComponent(requesterId, BombFireDelay.class);
        ed.setComponent(requesterId, bfd.copy());
        return true;
    }

    /** Stamp a fresh {@code GravityBombFireDelay} on {@code requester}. */
    static boolean setCoolDownGravityBomb(
            final EntityData ed, final EntitySet gravityBombs, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!gravityBombs.contains(requester)) {
            return false;
        }
        final GravityBombFireDelay bfd = ed.getComponent(requesterId, GravityBombFireDelay.class);
        ed.setComponent(requesterId, bfd.copy());
        return true;
    }

    /** Stamp a fresh {@code MineFireDelay} on {@code requester}. */
    static boolean setCoolDownMine(
            final EntityData ed, final EntitySet mines, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!mines.contains(requester)) {
            return false;
        }
        final MineFireDelay bfd = ed.getComponent(requesterId, MineFireDelay.class);
        ed.setComponent(requesterId, bfd.copy());
        return true;
    }

    // -----------------------------------------------------------------
    // Cost deductors — subtract the cost component from ship Health
    // -----------------------------------------------------------------

    /**
     * Top-level cost dispatch — debit the matching {@code Cost} component's
     * value from {@code requester}'s Health via {@code EnergySystem.damage}.
     * Returns whether the cost was deducted (false if cost > current health,
     * or the ship doesn't carry the matching weapon-inventory component).
     * Burst has no cost yet — falls through to inventory presence only.
     */
    static boolean deductCostOfAttack(
            final EntityData ed,
            final EnergySystem energy,
            final EntitySet bullets,
            final EntitySet bombs,
            final EntitySet gravityBombs,
            final EntitySet mines,
            final EntitySet bursts,
            final Entity requester,
            final byte flag) {
        if (requester == null) {
            return false;
        }
        if (flag == WeaponsSystem.BULLET) {
            return deductCostOfAttackBullet(ed, energy, bullets, requester);
        }
        if (flag == WeaponsSystem.BOMB) {
            return deductCostOfAttackBomb(ed, energy, bombs, requester);
        }
        if (flag == WeaponsSystem.GRAVBOMB) {
            return deductCostOfAttackGravityBomb(ed, energy, gravityBombs, requester);
        }
        if (flag == WeaponsSystem.MINE) {
            return deductCostOfAttackMine(ed, energy, mines, requester);
        }
        if (flag == WeaponsSystem.BURST) {
            // No cost on this for now — TODO: Add cost to burst
            return bursts.contains(requester);
        }
        return false;
    }

    /** Debit {@code BulletCost} from {@code requester}'s Health. */
    static boolean deductCostOfAttackBullet(
            final EntityData ed, final EnergySystem energy,
            final EntitySet bullets, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bullets.contains(requester)) {
            return false;
        }
        final BulletCost gc = ed.getComponent(requesterId, BulletCost.class);
        if (gc.getCost() > energy.getHealth(requesterId)) {
            return false;
        }
        energy.damage(requesterId, gc.getCost());
        return true;
    }

    /** Debit {@code BombCost} from {@code requester}'s Health. */
    static boolean deductCostOfAttackBomb(
            final EntityData ed, final EnergySystem energy,
            final EntitySet bombs, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bombs.contains(requester)) {
            return false;
        }
        final BombCost bc = ed.getComponent(requesterId, BombCost.class);
        if (bc.getCost() > energy.getHealth(requesterId)) {
            return false;
        }
        energy.damage(requesterId, bc.getCost());
        return true;
    }

    /** Debit {@code GravityBombCost} from {@code requester}'s Health. */
    static boolean deductCostOfAttackGravityBomb(
            final EntityData ed, final EnergySystem energy,
            final EntitySet gravityBombs, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!gravityBombs.contains(requester)) {
            return false;
        }
        final GravityBombCost bc = ed.getComponent(requesterId, GravityBombCost.class);
        if (bc.getCost() > energy.getHealth(requesterId)) {
            return false;
        }
        energy.damage(requesterId, bc.getCost());
        return true;
    }

    /** Debit {@code MineCost} from {@code requester}'s Health. */
    static boolean deductCostOfAttackMine(
            final EntityData ed, final EnergySystem energy,
            final EntitySet mines, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!mines.contains(requester)) {
            return false;
        }
        final MineCost bc = ed.getComponent(requesterId, MineCost.class);
        if (bc.getCost() > energy.getHealth(requesterId)) {
            return false;
        }
        energy.damage(requesterId, bc.getCost());
        return true;
    }
}
