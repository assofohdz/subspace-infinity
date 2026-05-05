// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
