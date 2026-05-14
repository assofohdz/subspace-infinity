// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.es.ship.weapons.WeaponType;

/** Pure-function weapons helpers (no ECS/physics deps); companion to {@link WeaponsFireSystem}/{@link WeaponsDamageLogic}. */
final class WeaponsLogic {

    private WeaponsLogic() {}

    /** Splash radius: multiplicative per level (L1=base, L2=2×, L3=3×, L4=4×) — Subspace canon. */
    static double splashRadiusForLevel(final double baseRadius, final int level) {
        return baseRadius * level;
    }

    /** Proximity radius: additive per level (L1=base, L2=base+1, …) — REFERENCE.md ## Bomb. */
    static double proximityRadiusForLevel(final int baseTiles, final int level) {
        return (double) baseTiles + (level - 1);
    }

    /** Subspace velocity × scale, clamped to ±{@code maxJmeAbs}; sign preserved for backward-firing presets. */
    static double effectiveProjectileSpeed(
            final int subspaceValue, final double scale, final double maxJmeAbs) {
        final double translated = subspaceValue * scale;
        if (translated >= 0.0) {
            return Math.min(translated, maxJmeAbs);
        }
        return Math.max(translated, -maxJmeAbs);
    }

    /** Recoil impulse opposite ship forward; zero when {@code subspaceThrust == 0}. */
    static Vec3d recoilImpulse(
            final int subspaceThrust,
            final double scale,
            final double maxJmeAbs,
            final Quatd shipOrientation) {
        if (subspaceThrust == 0) {
            return new Vec3d();
        }
        final double effective = effectiveProjectileSpeed(subspaceThrust, scale, maxJmeAbs);
        final Vec3d bodyForward = shipOrientation.mult(new Vec3d(0, 0, 1));
        return bodyForward.mult(-effective);
    }

    /** FF tri-state: null freq always damages; mode 0 swallows same-team, mode 1 allows splash, mode 2 allows all. */
    static boolean shouldDamageVictim(
            final Integer attackerFreq,
            final Integer victimFreq,
            final int friendlyFireMode,
            final boolean isSplash) {
        if (attackerFreq == null || victimFreq == null) {
            return true;
        }
        if (!attackerFreq.equals(victimFreq)) {
            return true;
        }
        if (isSplash) {
            return friendlyFireMode >= 1;
        }
        return friendlyFireMode >= 2;
    }

    /** BombSafety per-victim: true iff this victim is an enemy inside {@code radius} of the firing ship. */
    static boolean victimBlocksBombFire(
            final Integer ownerFreq,
            final Integer victimFreq,
            final Vec3d ownerPos,
            final Vec3d victimPos,
            final double radius) {
        if (radius <= 0.0) {
            return false;
        }
        if (!ProximityFuseSystem.shouldArmOn(ownerFreq, victimFreq)) {
            return false;
        }
        final double dx = victimPos.x - ownerPos.x;
        final double dy = victimPos.y - ownerPos.y;
        final double dz = victimPos.z - ownerPos.z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** Offset projectile spawn off the ship by its collision radius so it doesn't immediately re-collide. */
    static void applyProjectileRadiusOffset(
            final Vec3d projectilePosition,
            final byte weaponFlag,
            final double bulletRadius,
            final double bombRadius) {
        switch (weaponFlag) {
            case WeaponType.BULLET:
            case WeaponType.BURST:
                projectilePosition.addLocal(0, 0, bulletRadius);
                break;
            case WeaponType.BOMB:
            case WeaponType.GRAVBOMB:
            case WeaponType.MINE:
                projectilePosition.addLocal(0, 0, bombRadius);
                break;
            default:
                throw new AssertionError();
        }
    }
}
