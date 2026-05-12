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
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombStats;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.BulletStats;
import infinity.es.ship.weapons.GravityBombCost;
import infinity.es.ship.weapons.GravityBombFireDelay;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineStats;
import infinity.es.ship.weapons.WeaponType;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;

/** Pre-fire eligibility + cooldown stamp + cost deduction; canonical writer (post-spawn) for {@code *FireDelay}. */
final class WeaponsEligibility {

    private WeaponsEligibility() {}

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
            case WeaponType.BULLET:
                return canAttackBullet(ed, bullets, energy, requester);
            case WeaponType.BOMB:
                return canAttackBomb(ed, cr, physicsSpace, energy, bombs, energyEntities, requester);
            case WeaponType.GRAVBOMB:
                return canAttackGravityBomb(ed, gravityBombs, energy, requester);
            case WeaponType.MINE:
                return canAttackMine(ed, mines, energy, requester);
            case WeaponType.BURST:
                return canAttackBurst(bursts, requester);
            default:
                return false;
        }
    }

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
        final BulletStats stats = ed.getComponent(requesterId, BulletStats.class);
        return stats != null && stats.fireCostEnergy() <= energy.getHealth(requesterId);
    }

    /** Bomb eligibility — adds bomb-safety scan (rejects fire when an enemy is inside proximity-arm radius). */
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
        final BombStats stats = ed.getComponent(requesterId, BombStats.class);
        if (stats == null || stats.fireCostEnergy() > energy.getHealth(requesterId)) {
            return false;
        }
        return bombSafetyClear(ed, cr, physicsSpace, bombs, energyEntities, requesterId);
    }

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
        final MineStats stats = ed.getComponent(requesterId, MineStats.class);
        return stats != null && stats.dropCostEnergy() <= energy.getHealth(requesterId);
    }

    /** Inventory presence only — no cooldown/cost yet. */
    static boolean canAttackBurst(final EntitySet bursts, final Entity requester) {
        return bursts.contains(requester);
    }

    /** Rejects bomb fire when an enemy sits inside proximity-arm radius; no-op when arena's BombSafety is off. */
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
                continue;
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

    /** Bomb-safety scan radius after BombConfig toggle + per-level scaling; 0.0 = safety off (caller early-outs). */
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

    private static ConfigRegistry weaponsFor(
            final EntityData ed, final ConfigRegistrySystem cr, final EntityId attacker) {
        final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
        if (arenaId == null) {
            return ConfigRegistry.EMPTY;
        }
        return cr.forArena(arenaId);
    }

    /** Stamps a fresh {@code *FireDelay} on the ship; burst has no cooldown yet. */
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
        if (flag == WeaponType.BULLET) {
            return setCoolDownBullet(ed, bullets, requester);
        }
        if (flag == WeaponType.BOMB) {
            return setCoolDownBomb(ed, bombs, requester);
        }
        if (flag == WeaponType.GRAVBOMB) {
            return setCoolDownGravityBomb(ed, gravityBombs, requester);
        }
        if (flag == WeaponType.MINE) {
            return setCoolDownMine(ed, mines, requester);
        }
        if (flag == WeaponType.BURST) {
            return bursts.contains(requester);
        }
        return false;
    }

    static boolean setCoolDownBullet(
            final EntityData ed, final EntitySet bullets, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bullets.contains(requester)) {
            return false;
        }
        final BulletStats stats = ed.getComponent(requesterId, BulletStats.class);
        if (stats == null) {
            return false;
        }
        ed.setComponent(requesterId, new BulletFireDelay(stats.fireDelayMillis()));
        return true;
    }

    static boolean setCoolDownBomb(
            final EntityData ed, final EntitySet bombs, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bombs.contains(requester)) {
            return false;
        }
        final BombStats stats = ed.getComponent(requesterId, BombStats.class);
        if (stats == null) {
            return false;
        }
        ed.setComponent(requesterId, new BombFireDelay(stats.fireDelayMillis()));
        return true;
    }

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

    static boolean setCoolDownMine(
            final EntityData ed, final EntitySet mines, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!mines.contains(requester)) {
            return false;
        }
        final MineStats stats = ed.getComponent(requesterId, MineStats.class);
        if (stats == null) {
            return false;
        }
        ed.setComponent(requesterId, new MineFireDelay(stats.fireDelayMillis()));
        return true;
    }

    /** Debits per-weapon cost via attributed {@link EnergySystem#damage(EntityId,int,EntityId,byte)}; burst has no cost yet. */
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
        if (flag == WeaponType.BULLET) {
            return deductCostOfAttackBullet(ed, energy, bullets, requester);
        }
        if (flag == WeaponType.BOMB) {
            return deductCostOfAttackBomb(ed, energy, bombs, requester);
        }
        if (flag == WeaponType.GRAVBOMB) {
            return deductCostOfAttackGravityBomb(ed, energy, gravityBombs, requester);
        }
        if (flag == WeaponType.MINE) {
            return deductCostOfAttackMine(ed, energy, mines, requester);
        }
        if (flag == WeaponType.BURST) {
            // TODO: Add cost to burst.
            return bursts.contains(requester);
        }
        return false;
    }

    static boolean deductCostOfAttackBullet(
            final EntityData ed, final EnergySystem energy,
            final EntitySet bullets, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bullets.contains(requester)) {
            return false;
        }
        final BulletStats stats = ed.getComponent(requesterId, BulletStats.class);
        if (stats == null || stats.fireCostEnergy() > energy.getHealth(requesterId)) {
            return false;
        }
        energy.damage(requesterId, stats.fireCostEnergy(), requesterId, WeaponType.BULLET);
        return true;
    }

    static boolean deductCostOfAttackBomb(
            final EntityData ed, final EnergySystem energy,
            final EntitySet bombs, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!bombs.contains(requester)) {
            return false;
        }
        final BombStats stats = ed.getComponent(requesterId, BombStats.class);
        if (stats == null || stats.fireCostEnergy() > energy.getHealth(requesterId)) {
            return false;
        }
        energy.damage(requesterId, stats.fireCostEnergy(), requesterId, WeaponType.BOMB);
        return true;
    }

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
        energy.damage(requesterId, bc.getCost(), requesterId, WeaponType.GRAVBOMB);
        return true;
    }

    static boolean deductCostOfAttackMine(
            final EntityData ed, final EnergySystem energy,
            final EntitySet mines, final Entity requester) {
        final EntityId requesterId = requester.getId();
        if (!mines.contains(requester)) {
            return false;
        }
        final MineStats stats = ed.getComponent(requesterId, MineStats.class);
        if (stats == null || stats.dropCostEnergy() > energy.getHealth(requesterId)) {
            return false;
        }
        energy.damage(requesterId, stats.dropCostEnergy(), requesterId, WeaponType.MINE);
        return true;
    }
}
