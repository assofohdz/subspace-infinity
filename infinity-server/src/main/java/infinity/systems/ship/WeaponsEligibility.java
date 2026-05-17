// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
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
import infinity.es.ChangeTarget;
import infinity.es.Frequency;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstChange;
import infinity.es.ship.actions.InventoryCount;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombSafetyRadius;
import infinity.es.ship.weapons.BombStats;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.BulletStats;
import infinity.es.ship.weapons.EnergyCost;
import infinity.es.ship.weapons.FireDelay;
import infinity.es.ship.weapons.GravBomb;
import infinity.es.ship.weapons.GravBombChange;
import infinity.es.ship.weapons.GravBombStats;
import infinity.es.ship.weapons.GravityBombFireDelay;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineStats;
import infinity.es.ship.weapons.WeaponType;
import java.util.function.Function;

/** Pre-fire eligibility + cooldown stamp + cost deduction; canonical writer (post-spawn) for {@code *FireDelay}. Energy weapons and inventory weapons share generic helpers; per-weapon variation collapses to a {@code (Class, accessor)} pair at the dispatch site. */
final class WeaponsEligibility {

    private WeaponsEligibility() {}

    // Eligibility-check payload: ed + physics + energy + 5 per-weapon EntitySets + requester + weaponType; orchestrator dispatch shape.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    static boolean canAttack(
            final EntityData ed,
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
                return canAttackEnergyWeapon(ed, energy, bullets, requester,
                        BulletFireDelay.class, BulletStats.class);
            case WeaponType.BOMB:
                return canAttackEnergyWeapon(ed, energy, bombs, requester,
                                BombFireDelay.class, BombStats.class)
                        && bombSafetyClear(ed, physicsSpace, bombs, energyEntities, requester.getId());
            case WeaponType.GRAVBOMB:
                return canAttackInventoryWeaponWithDelay(ed, gravityBombs, requester,
                        GravBomb.class, GravityBombFireDelay.class);
            case WeaponType.MINE:
                return canAttackEnergyWeapon(ed, energy, mines, requester,
                        MineFireDelay.class, MineStats.class);
            case WeaponType.BURST:
                return canAttackInventoryWeapon(ed, bursts, requester, Burst.class);
            default:
                return false;
        }
    }

    /** Energy-cost weapon eligibility: membership + cooldown ready + cost-source present + cost &le; current health. */
    static boolean canAttackEnergyWeapon(
            final EntityData ed,
            final EnergySystem energy,
            final EntitySet set,
            final Entity requester,
            final Class<? extends FireDelay> delayClass,
            final Class<? extends EnergyCost> costClass) {
        if (!set.contains(requester)) {
            return false;
        }
        final EntityId id = requester.getId();
        final FireDelay delay = ed.getComponent(id, delayClass);
        if (delay == null || delay.getPercent() < 1) {
            return false;
        }
        final EnergyCost cost = ed.getComponent(id, costClass);
        return cost != null && cost.energyCost() <= energy.getHealth(id);
    }

    /** Inventory-cost weapon eligibility: membership + inventory count &gt; 0. Subspace-canonical alternative to energy-cost (e.g. {@code Burst}, no {@code BurstEnergy} in canon). */
    static boolean canAttackInventoryWeapon(
            final EntityData ed,
            final EntitySet set,
            final Entity requester,
            final Class<? extends InventoryCount> inventoryClass) {
        if (!set.contains(requester)) {
            return false;
        }
        final InventoryCount inv = ed.getComponent(requester.getId(), inventoryClass);
        return inv != null && inv.count() > 0;
    }

    /** Inventory + cooldown eligibility (Infinity gravbomb shape): membership + inventory count &gt; 0 + cooldown ready. */
    static boolean canAttackInventoryWeaponWithDelay(
            final EntityData ed,
            final EntitySet set,
            final Entity requester,
            final Class<? extends InventoryCount> inventoryClass,
            final Class<? extends FireDelay> delayClass) {
        if (!set.contains(requester)) {
            return false;
        }
        final EntityId id = requester.getId();
        final InventoryCount inv = ed.getComponent(id, inventoryClass);
        if (inv == null || inv.count() <= 0) {
            return false;
        }
        final FireDelay delay = ed.getComponent(id, delayClass);
        return delay != null && delay.getPercent() >= 1;
    }

    /** Rejects bomb fire when an enemy sits inside proximity-arm radius; no-op when arena's BombSafety is off. */
    static boolean bombSafetyClear(
            final EntityData ed,
            final PhysicsSpace<EntityId, MBlockShape> physicsSpace,
            final EntitySet bombs,
            final EntitySet energyEntities,
            final EntityId requesterId) {
        final RigidBody<EntityId, MBlockShape> ownerBody =
                physicsSpace.getBinIndex().getRigidBody(requesterId);
        if (ownerBody == null) {
            return true;
        }
        final double radius = effectiveBombSafetyRadius(ed, bombs, requesterId);
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

    /**
     * Bomb-safety scan radius from per-ship {@code BombSafetyRadius} snapshot + bomb-level scaling;
     * 0.0 = safety off (caller early-outs). Snapshot is projected at ship spawn from arena
     * {@code BombConfig} per ADR-0002 — no hot-path config read.
     */
    static double effectiveBombSafetyRadius(
            final EntityData ed,
            final EntitySet bombs,
            final EntityId requesterId) {
        final BombSafetyRadius safety = ed.getComponent(requesterId, BombSafetyRadius.class);
        if (safety == null || !safety.enabled() || safety.baseTiles() <= 0) {
            return 0.0;
        }
        final BombCurrentLevel bombLevel =
                bombs.getEntity(requesterId).get(BombCurrentLevel.class);
        return WeaponsLogic.proximityRadiusForLevel(
                safety.baseTiles(), bombLevel.getLevel().level);
    }

    /** Stamps a fresh {@code *FireDelay} on the ship; burst has no cooldown yet; gravbomb refreshes the existing delay rather than rebuilding from stats. */
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
            return setCoolDownEnergyWeapon(ed, bullets, requester,
                    BulletStats.class, s -> new BulletFireDelay(s.fireDelayMillis()));
        }
        if (flag == WeaponType.BOMB) {
            return setCoolDownEnergyWeapon(ed, bombs, requester,
                    BombStats.class, s -> new BombFireDelay(s.fireDelayMillis()));
        }
        if (flag == WeaponType.GRAVBOMB) {
            return setCoolDownEnergyWeapon(ed, gravityBombs, requester,
                    GravBombStats.class, s -> new GravityBombFireDelay(s.fireDelayMillis()));
        }
        if (flag == WeaponType.MINE) {
            return setCoolDownEnergyWeapon(ed, mines, requester,
                    MineStats.class, s -> new MineFireDelay(s.fireDelayMillis()));
        }
        if (flag == WeaponType.BURST) {
            return bursts.contains(requester);
        }
        return false;
    }

    /** Energy-cost weapon: read stats, build a fresh per-weapon FireDelay; caller's lambda keeps the {@code new *FireDelay(...)} visible to {@code CanonicalWriterTest}. */
    static <S extends EntityComponent> boolean setCoolDownEnergyWeapon(
            final EntityData ed,
            final EntitySet set,
            final Entity requester,
            final Class<S> statsClass,
            final Function<S, ? extends FireDelay> delayFromStats) {
        if (!set.contains(requester)) {
            return false;
        }
        final EntityId id = requester.getId();
        final S stats = ed.getComponent(id, statsClass);
        if (stats == null) {
            return false;
        }
        ed.setComponent(id, delayFromStats.apply(stats));
        return true;
    }

    /** Debits per-weapon cost: energy weapons via {@link EnergySystem#damage(EntityId,int,EntityId,byte)}; inventory weapons via a {@code *Change(-1)} Change holder drained by the per-type canonical writer. */
    // Cost-deduction payload: ed + energy + 5 per-weapon EntitySets + requester + flag; orchestrator dispatch shape.
    @SuppressWarnings("PMD.ExcessiveParameterList")
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
            return deductEnergyCost(ed, energy, bullets, requester,
                    BulletStats.class, WeaponType.BULLET);
        }
        if (flag == WeaponType.BOMB) {
            return deductEnergyCost(ed, energy, bombs, requester,
                    BombStats.class, WeaponType.BOMB);
        }
        if (flag == WeaponType.GRAVBOMB) {
            return deductInventoryWeapon(ed, gravityBombs, requester, new GravBombChange(-1));
        }
        if (flag == WeaponType.MINE) {
            return deductEnergyCost(ed, energy, mines, requester,
                    MineStats.class, WeaponType.MINE);
        }
        if (flag == WeaponType.BURST) {
            return deductInventoryWeapon(ed, bursts, requester, new BurstChange(-1));
        }
        return false;
    }

    /** Energy-cost weapon: attribute cost to the firer via {@link EnergySystem#damage}. Cost source implements {@link EnergyCost}. */
    static boolean deductEnergyCost(
            final EntityData ed,
            final EnergySystem energy,
            final EntitySet set,
            final Entity requester,
            final Class<? extends EnergyCost> costClass,
            final byte weaponType) {
        if (!set.contains(requester)) {
            return false;
        }
        final EntityId id = requester.getId();
        final EnergyCost source = ed.getComponent(id, costClass);
        if (source == null) {
            return false;
        }
        final int amount = source.energyCost();
        if (amount > energy.getHealth(id)) {
            return false;
        }
        // EnergyChange convention: positive = heal, negative = damage. Energy-cost is
        // a SELF-DAMAGE deduction; negate at the call site.
        energy.damage(id, -amount, id, weaponType);
        return true;
    }

    /** Inventory-cost weapon: emits a one-shot {@code ChangeTarget.self(ship) + *Change(-1)} Change holder; drained by the per-type canonical writer (e.g. {@code BurstSystem}). */
    static boolean deductInventoryWeapon(
            final EntityData ed,
            final EntitySet set,
            final Entity requester,
            final EntityComponent change) {
        if (!set.contains(requester)) {
            return false;
        }
        final EntityId holder = ed.createEntity();
        ed.setComponents(holder, ChangeTarget.self(requester.getId()), change);
        return true;
    }
}
