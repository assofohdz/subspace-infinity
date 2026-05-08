// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.sim.CorePhysicsConstants;

/**
 * Pure-function helpers extracted from {@link WeaponsSystem} so the giant
 * weapons class stays under a sane class-level cyclomatic-complexity ceiling.
 *
 * <p>Every method here is {@code static}, has no ECS / physics / arena
 * dependency, and is unit-testable directly (most are pinned by
 * {@code WeaponsSystemSplashTest} and {@code WeaponsSystemRecoilTest} —
 * those tests target this class via the same package-private visibility
 * the original methods had).
 *
 * <p>This is the "tightly coupled class" escape hatch from the round-19
 * splitting recipe: when the per-weapon-family methods can't be cleanly
 * moved to per-family subsystems (shared EntitySets, single ContactListener
 * pipeline, etc.), pull the genuinely-context-free helpers here instead.
 */
final class WeaponsLogic {

    private WeaponsLogic() {
        // utility class — instantiation prevented
    }

    /**
     * Pure-function projection of {@code BombConfig.explodeRadius} (tiles /
     * world units) onto a per-bomb splash radius. Subspace canon scaling:
     * L1 base, L2×2, L3×3, L4×4. Exposed package-private so unit tests can
     * pin per-level scaling without bringing up the spawn pipeline.
     */
    static double splashRadiusForLevel(final double baseRadius, final int level) {
        return baseRadius * level;
    }

    /**
     * Pure-function projection of {@code BombConfig.proximityDistance} (tiles)
     * onto a per-bomb proximity-arm radius. Subspace canon scaling: each level
     * <em>adds</em> 1 tile (L1=base, L2=base+1, L3=base+2, L4=base+3) — REFERENCE.md
     * ## Bomb {@code "Each level adds 1"}. Distinct from the multiplicative
     * scaling on {@link #splashRadiusForLevel}. Exposed package-private so unit
     * tests can pin per-level scaling without bringing up the spawn pipeline.
     */
    static double proximityRadiusForLevel(final int baseTiles, final int level) {
        return baseTiles + (level - 1);
    }

    /**
     * Slice 10 — pure-function projectile-speed translation. Multiplies the
     * raw Subspace velocity unit value (from {@code BulletSpeed}, {@code BombSpeed},
     * or {@code BurstSpeed} component) by the engine-tier
     * {@code subspaceVelocityScale}, then clamps the absolute value to the
     * engine-tier {@code maxProjectileSpeedJme} cap to prevent legacy outliers
     * (e.g. trench javelin's {@code BulletSpeed 64636}) from producing
     * physics-breaking velocities.
     *
     * <p>Negative inputs preserve sign so backward-firing presets (e.g.
     * trench javelin's {@code bulletSpeed: -900} per slice 10b) work
     * without consumer-side special-casing.
     *
     * <p>Exposed package-private so unit tests can pin scale + cap behaviour
     * without bringing up an ECS / arena fixture.
     */
    static double effectiveProjectileSpeed(
            final int subspaceValue, final double scale, final double maxJmeAbs) {
        final double translated = subspaceValue * scale;
        if (translated >= 0.0) {
            return Math.min(translated, maxJmeAbs);
        }
        return Math.max(translated, -maxJmeAbs);
    }

    /**
     * Pure-function recoil impulse computation for
     * {@code WeaponsSystem.applyBombRecoil}. Exposed package-private so tests
     * can pin direction + magnitude rules without bringing up an ECS / physics
     * fixture.
     *
     * @param subspaceThrust raw {@code BombThrust} value (Subspace velocity
     *     units; SVS canon {@code 400} for warbirds)
     * @param scale {@code EngineConfig.subspaceVelocityScale}
     * @param maxJmeAbs {@code EngineConfig.maxProjectileSpeedJme} cap
     * @param shipOrientation ship orientation at fire time
     * @return impulse vector in jME world units / sec (= velocity delta);
     *     zero vector when {@code subspaceThrust == 0}
     */
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

    /**
     * Pure-function friendly-fire decision — exposed package-private so unit
     * tests can pin the tri-state behaviour without bringing up an ECS / arena
     * system fixture. {@code null} freq means "no team" (NPC, prize, debris) →
     * always damage.
     *
     * <ul>
     *   <li>If attacker or victim has no freq, no FF gate applies and damage
     *       goes through.
     *   <li>If teams differ, damage goes through (true enemy hit).
     *   <li>If teams match: mode 0 swallows; mode 1 allows splash but not direct
     *       hits; mode 2 allows everything.
     * </ul>
     */
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

    /**
     * Per-victim slice 9c-BombSafety decision: true iff this single victim
     * blocks bomb fire — i.e., it's an enemy (per
     * {@code ProximityFuseSystem.shouldArmOn}) sitting within {@code radius}
     * of the firing ship.
     *
     * <p>Pure function — no ECS / physics deps. The instance-side scan in
     * {@code WeaponsSystem.bombSafetyClear} walks the {@code energyEntities}
     * EntitySet, resolves each victim's {@code Frequency} + {@code body.position},
     * and calls this helper.
     *
     * @param ownerFreq firing ship's freq; {@code null} = no team
     * @param victimFreq victim's freq; {@code null} = no team
     * @param ownerPos firing ship's body position
     * @param victimPos victim's body position
     * @param radius effective proximity-arm radius (per-level scaled)
     */
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

    /**
     * Step 4 of the attack-info pipeline: nudge the projectile spawn point off
     * the ship by the projectile's own collision radius so it doesn't
     * immediately re-collide with us. Bullets / bursts use the bullet radius;
     * bombs / grav-bombs / mines use the (larger) bomb radius.
     */
    static void applyProjectileRadiusOffset(
            final Vec3d projectilePosition, final byte weaponFlag) {
        switch (weaponFlag) {
            case WeaponsSystem.BULLET:
            case WeaponsSystem.BURST:
                projectilePosition.addLocal(0, 0, CorePhysicsConstants.BULLETSIZERADIUS);
                break;
            case WeaponsSystem.BOMB:
            case WeaponsSystem.GRAVBOMB:
            case WeaponsSystem.MINE:
                projectilePosition.addLocal(0, 0, CorePhysicsConstants.BOMBSIZERADIUS);
                break;
            default:
                throw new AssertionError();
        }
    }
}
