// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Vec3d;
import org.junit.Test;

/**
 * Pins the slice-9a static helpers on {@link WeaponsSystem} that drive the
 * splash damage path:
 *
 * <ul>
 *   <li>{@code splashRadiusForLevel} — Subspace per-level scaling and the 16
 *       px / tile world-unit conversion that the fire-time projection in
 *       {@code createProjectileBomb} applies.
 *   <li>{@code shouldDamageVictim} — the friendly-fire tri-state gate (mode
 *       0/1/2) shared by direct-hit and splash damage paths.
 * </ul>
 *
 * <p>These are pure functions extracted specifically so the FF + projection
 * contracts can be pinned without standing up a full ECS / physics fixture.
 * The system-level integration (broadphase scan, {@code EnergySystem}
 * feedback) is deferred to a future heavier integration test.
 */
public class WeaponsSystemSplashTest {

  // -----------------------------------------------------------------
  // splashRadiusForLevel — per-level scaling + pixels→world unit
  // -----------------------------------------------------------------

  @Test
  public void splashRadiusForLevel_appliesPerLevelMultiplier() {
    // Trench / testarena / testconf canon: explodeRadius = 5 tiles
    // → L1 = 5, L2 = 10, L3 = 15, L4 = 20.
    final double baseRadius = 5.0;
    assertEquals(5.0, WeaponsSystem.splashRadiusForLevel(baseRadius, 1), 1e-9);
    assertEquals(10.0, WeaponsSystem.splashRadiusForLevel(baseRadius, 2), 1e-9);
    assertEquals(15.0, WeaponsSystem.splashRadiusForLevel(baseRadius, 3), 1e-9);
    assertEquals(20.0, WeaponsSystem.splashRadiusForLevel(baseRadius, 4), 1e-9);
  }

  @Test
  public void splashRadiusForLevel_zeroBase_yieldsZero() {
    // explodeRadius = 0 disables splash (createProjectileBomb skips the
    // SplashDamage stamp when the result is non-positive).
    for (int level = 1; level <= 4; level++) {
      assertEquals(0.0, WeaponsSystem.splashRadiusForLevel(0.0, level), 0.0);
    }
  }

  // -----------------------------------------------------------------
  // proximityRadiusForLevel — per-level ADDITIVE scaling (slice 9b)
  // -----------------------------------------------------------------

  @Test
  public void proximityRadiusForLevel_addsOnePerLevel() {
    // Trench / testarena / testconf canon: ProximityDistance = 3 tiles
    // → L1 = 3, L2 = 4, L3 = 5, L4 = 6 (REFERENCE.md ## Bomb: "Each level
    // adds 1"). Distinct from splash's multiplicative scaling.
    final int base = 3;
    assertEquals(3.0, WeaponsSystem.proximityRadiusForLevel(base, 1), 1e-9);
    assertEquals(4.0, WeaponsSystem.proximityRadiusForLevel(base, 2), 1e-9);
    assertEquals(5.0, WeaponsSystem.proximityRadiusForLevel(base, 3), 1e-9);
    assertEquals(6.0, WeaponsSystem.proximityRadiusForLevel(base, 4), 1e-9);
  }

  @Test
  public void proximityRadiusForLevel_zeroBaseStillScales() {
    // base = 0 still yields 0/1/2/3 — but createProjectileBomb gates on
    // proximityDistance > 0 before stamping ProximityFuse, so a base-0
    // arena disables proximity entirely regardless of level.
    assertEquals(0.0, WeaponsSystem.proximityRadiusForLevel(0, 1), 1e-9);
    assertEquals(1.0, WeaponsSystem.proximityRadiusForLevel(0, 2), 1e-9);
    assertEquals(2.0, WeaponsSystem.proximityRadiusForLevel(0, 3), 1e-9);
    assertEquals(3.0, WeaponsSystem.proximityRadiusForLevel(0, 4), 1e-9);
  }

  // -----------------------------------------------------------------
  // shouldDamageVictim — friendly-fire tri-state gate
  // -----------------------------------------------------------------

  @Test
  public void enemyHit_alwaysDamages_regardlessOfMode() {
    // Different freqs = enemy → damage applies under every FF mode for both
    // direct hits and splash.
    for (int mode = 0; mode <= 2; mode++) {
      assertTrue(
          "enemy direct-hit must damage (mode=" + mode + ")",
          WeaponsSystem.shouldDamageVictim(0, 1, mode, false));
      assertTrue(
          "enemy splash must damage (mode=" + mode + ")",
          WeaponsSystem.shouldDamageVictim(0, 1, mode, true));
    }
  }

  @Test
  public void mode0_off_swallowsAllSameTeamDamage() {
    // Mode 0 is the safe default — same-team weapons never apply damage.
    assertFalse(
        "mode 0 same-team direct → no damage",
        WeaponsSystem.shouldDamageVictim(0, 0, 0, false));
    assertFalse(
        "mode 0 same-team splash → no damage",
        WeaponsSystem.shouldDamageVictim(0, 0, 0, true));
  }

  @Test
  public void mode1_bombSplashOnly_gatesDirectHits() {
    // Mode 1 is the slice 9a "bomb friendly fire only" point: same-team
    // splash applies, but a direct-hit (bullet, burst, mine, gravbomb
    // direct) is still swallowed.
    assertFalse(
        "mode 1 same-team direct → no damage",
        WeaponsSystem.shouldDamageVictim(0, 0, 1, false));
    assertTrue(
        "mode 1 same-team splash → damage",
        WeaponsSystem.shouldDamageVictim(0, 0, 1, true));
  }

  @Test
  public void mode2_allFriendlyFire_letsBothDamagePaths() {
    assertTrue(
        "mode 2 same-team direct → damage",
        WeaponsSystem.shouldDamageVictim(0, 0, 2, false));
    assertTrue(
        "mode 2 same-team splash → damage",
        WeaponsSystem.shouldDamageVictim(0, 0, 2, true));
  }

  @Test
  public void unknownTeam_alwaysDamages() {
    // NPC / prize / debris with no Frequency component → the gate ignores FF
    // mode and damages. Captures the "non-team participant" pass-through that
    // the helper documents.
    assertTrue(
        "null attacker freq → damage",
        WeaponsSystem.shouldDamageVictim(null, 0, 0, false));
    assertTrue(
        "null victim freq → damage",
        WeaponsSystem.shouldDamageVictim(0, null, 0, false));
    assertTrue(
        "both null → damage",
        WeaponsSystem.shouldDamageVictim(null, null, 0, true));
  }

  // -----------------------------------------------------------------
  // victimBlocksBombFire — slice 9c-BombSafety per-victim decision
  // -----------------------------------------------------------------

  @Test
  public void victimBlocksBombFire_enemyInsideRadius_blocks() {
    // Trench L1 effective radius = 3 tiles. Enemy at 2 tiles distance is
    // strictly inside → block fire.
    final Vec3d owner = new Vec3d(0, 0, 0);
    final Vec3d enemy = new Vec3d(2, 0, 0);
    assertTrue(
        "enemy strictly inside radius → blocks fire",
        WeaponsSystem.victimBlocksBombFire(0, 1, owner, enemy, 3.0));
  }

  @Test
  public void victimBlocksBombFire_enemyAtExactRadius_blocks() {
    // Boundary-inclusive: an enemy exactly on the arming radius edge blocks
    // (would arm a real bomb if proximity-fused). Mirrors the
    // distance-squared <= radius-squared check.
    final Vec3d owner = new Vec3d(0, 0, 0);
    final Vec3d enemy = new Vec3d(3, 0, 0);
    assertTrue(
        "enemy at boundary → blocks fire",
        WeaponsSystem.victimBlocksBombFire(0, 1, owner, enemy, 3.0));
  }

  @Test
  public void victimBlocksBombFire_enemyOutsideRadius_doesNotBlock() {
    // 4 tiles away with a 3-tile arming radius → safe to fire.
    final Vec3d owner = new Vec3d(0, 0, 0);
    final Vec3d enemy = new Vec3d(4, 0, 0);
    assertFalse(
        "enemy outside radius → fire allowed",
        WeaponsSystem.victimBlocksBombFire(0, 1, owner, enemy, 3.0));
  }

  @Test
  public void victimBlocksBombFire_friendlyInsideRadius_doesNotBlock() {
    // FF gate (reused from ProximityFuseSystem.shouldArmOn): friendlies
    // never arm a proximity bomb, so they don't block fire either.
    final Vec3d owner = new Vec3d(0, 0, 0);
    final Vec3d friendly = new Vec3d(1, 0, 0);
    assertFalse(
        "same-team victim inside radius → fire allowed",
        WeaponsSystem.victimBlocksBombFire(0, 0, owner, friendly, 3.0));
  }

  @Test
  public void victimBlocksBombFire_zeroRadius_neverBlocks() {
    // proximityDistance == 0 (or per-level result <= 0) — guard rail
    // matching bombSafetyClear's caller-side early return. The pure helper
    // should also short-circuit so callers can rely on a single source of
    // truth for "0 radius means no scan."
    final Vec3d owner = new Vec3d(0, 0, 0);
    final Vec3d enemy = new Vec3d(0, 0, 0);
    assertFalse(
        "zero radius → no scan, fire allowed even on co-located enemy",
        WeaponsSystem.victimBlocksBombFire(0, 1, owner, enemy, 0.0));
  }

  @Test
  public void victimBlocksBombFire_unknownTeams_treatedAsEnemies() {
    // null freq on either side = "no team" (NPC, debris). Per
    // ProximityFuseSystem.shouldArmOn canon, an unteamed entity is a valid
    // arming target — and therefore blocks bomb fire when inside radius.
    final Vec3d owner = new Vec3d(0, 0, 0);
    final Vec3d victim = new Vec3d(1, 0, 0);
    assertTrue(
        "null owner freq + teamed victim → blocks (canonical NPC firer)",
        WeaponsSystem.victimBlocksBombFire(null, 0, owner, victim, 3.0));
    assertTrue(
        "teamed owner + null victim freq → blocks (canonical NPC victim)",
        WeaponsSystem.victimBlocksBombFire(0, null, owner, victim, 3.0));
    assertTrue(
        "both null → blocks (no FF gate engages)",
        WeaponsSystem.victimBlocksBombFire(null, null, owner, victim, 3.0));
  }

  // -----------------------------------------------------------------
  // effectiveProjectileSpeed — Slice 10 Subspace→jME translation + cap
  // -----------------------------------------------------------------

  @Test
  public void effectiveProjectileSpeed_appliesScale() {
    // SVS canon BulletSpeed=2000 with default scale 0.01 → 20 jME.
    assertEquals(20.0, WeaponsSystem.effectiveProjectileSpeed(2000, 0.01, 100.0), 1e-9);
    // Trench warbird's BulletSpeed=5000 with default scale 0.01 → 50 jME
    // (matches today's hardcoded addLocal(0,0,50) for bullets, by design).
    assertEquals(50.0, WeaponsSystem.effectiveProjectileSpeed(5000, 0.01, 100.0), 1e-9);
  }

  @Test
  public void effectiveProjectileSpeed_clampsAtCap() {
    // Trench javelin's legacy BulletSpeed=64636 with scale 0.01 → 646.36
    // would be physics-breaking; the cap at 100 keeps it safe.
    assertEquals(100.0, WeaponsSystem.effectiveProjectileSpeed(64636, 0.01, 100.0), 1e-9);
    // Boundary: exactly at the cap passes through.
    assertEquals(100.0, WeaponsSystem.effectiveProjectileSpeed(10000, 0.01, 100.0), 1e-9);
  }

  @Test
  public void effectiveProjectileSpeed_zeroBaseYieldsZero() {
    assertEquals(0.0, WeaponsSystem.effectiveProjectileSpeed(0, 0.01, 100.0), 0.0);
  }

  @Test
  public void effectiveProjectileSpeed_lowValueStaysLow() {
    // Trench shark's BulletSpeed=1 with scale 0.01 → 0.01 jME (essentially
    // stopped — preserves the "shark super slow bullets" gameplay intent).
    assertEquals(0.01, WeaponsSystem.effectiveProjectileSpeed(1, 0.01, 100.0), 1e-9);
  }

  @Test
  public void effectiveProjectileSpeed_negativePreservesSignAndClamps() {
    // Slice 10b will use negative values for backward firing; the helper
    // already supports it. Cap clamps the absolute value, sign survives.
    assertEquals(-9.0, WeaponsSystem.effectiveProjectileSpeed(-900, 0.01, 100.0), 1e-9);
    assertEquals(-100.0, WeaponsSystem.effectiveProjectileSpeed(-50000, 0.01, 100.0), 1e-9);
  }

  @Test
  public void effectiveProjectileSpeed_alternateScale() {
    // Operator-tunable: different engine.groovy scale produces a
    // proportionally different output for the same per-ship value.
    // 2000 × 0.05 = 100 (just hits the cap).
    assertEquals(100.0, WeaponsSystem.effectiveProjectileSpeed(2000, 0.05, 100.0), 1e-9);
    // 2000 × 0.02 = 40 (below cap, passes through).
    assertEquals(40.0, WeaponsSystem.effectiveProjectileSpeed(2000, 0.02, 100.0), 1e-9);
  }

  @Test
  public void victimBlocksBombFire_radiusIs3D() {
    // Scan respects all three axes — a victim above the firing ship still
    // counts. Trench's z-stack matters (ships at the same x/y but different
    // z don't count as colocated).
    final Vec3d owner = new Vec3d(0, 0, 0);
    final Vec3d enemyAbove = new Vec3d(0, 0, 2);
    assertTrue(
        "enemy 2 above (radius 3) → blocks",
        WeaponsSystem.victimBlocksBombFire(0, 1, owner, enemyAbove, 3.0));
    final Vec3d enemyDiag = new Vec3d(2, 2, 2); // dist² = 12 > 9
    assertFalse(
        "enemy at sqrt(12) ≈ 3.46 (radius 3) → outside, fire allowed",
        WeaponsSystem.victimBlocksBombFire(0, 1, owner, enemyDiag, 3.0));
  }
}
