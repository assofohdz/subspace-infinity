// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the static-helper seams of {@link ProximityFuseSystem} (slice 9b):
 *
 * <ul>
 *   <li>{@code shouldArmOn} — canonical Subspace arming gate. Same-team
 *       match → never arm; missing-freq on either side → arm (NPC /
 *       debris pass-through). Distinct from
 *       {@link WeaponsSystem#shouldDamageVictim} which gates damage
 *       through arena {@code friendlyFire} modes; arming has no FF mode
 *       knob (canonical: friendlies never trigger proximity).
 *   <li>{@code fuseElapsed} — boundary arithmetic on the
 *       {@code now − armedAt ≥ fuseMs} deadline check, including the
 *       exact-equality boundary and sub-fuse early reads.
 * </ul>
 *
 * <p>The system-level integration (per-tick scan, body-position lookup
 * via {@code PhysicsSpace.getBinIndex}, detonation through
 * {@link WeaponsSystem#detonateProjectile}) is deferred to the
 * spawn-projection harness backlog, same as the existing
 * splash-damage system-level scan in {@link WeaponsSystemSplashTest}.
 */
public class ProximityFuseSystemTest {

  // -----------------------------------------------------------------
  // shouldArmOn — arming-gate tri-state
  // -----------------------------------------------------------------

  @Test
  public void shouldArmOn_differentFreqs_arms() {
    // True enemy → arm.
    assertTrue("enemy (0 vs 1)", ProximityFuseSystem.shouldArmOn(0, 1));
    assertTrue("enemy (5 vs 7)", ProximityFuseSystem.shouldArmOn(5, 7));
  }

  @Test
  public void shouldArmOn_sameFreq_doesNotArm() {
    // Canonical Subspace VIE: friendlies never trigger proximity arming,
    // regardless of arena friendlyFire mode. Distinct from the damage
    // gate which IS sensitive to FF mode.
    assertFalse("same team (0 vs 0)", ProximityFuseSystem.shouldArmOn(0, 0));
    assertFalse("same team (3 vs 3)", ProximityFuseSystem.shouldArmOn(3, 3));
  }

  @Test
  public void shouldArmOn_nullFreqs_treatAsArmable() {
    // No-team participants (NPC, debris, prize) — arm.
    assertTrue("null owner", ProximityFuseSystem.shouldArmOn(null, 0));
    assertTrue("null victim", ProximityFuseSystem.shouldArmOn(0, null));
    assertTrue("both null", ProximityFuseSystem.shouldArmOn(null, null));
  }

  // -----------------------------------------------------------------
  // fuseElapsed — deadline arithmetic
  // -----------------------------------------------------------------

  @Test
  public void fuseElapsed_beforeFuseExpiry_isFalse() {
    final long armedAt = 1_000_000_000_000L; // 1000 sec in nanos
    // 50ms after arming, fuse=100ms → not yet elapsed.
    final long now = armedAt + 50L * 1_000_000L;
    assertFalse(ProximityFuseSystem.fuseElapsed(armedAt, now, 100L));
  }

  @Test
  public void fuseElapsed_exactlyAtFuseExpiry_isTrue() {
    // Boundary: now − armedAt == fuseMs (in nanos). Fires this tick — the
    // ≥ comparison is intentionally inclusive so the canonical fuse value
    // doesn't slip a frame waiting for "strictly greater".
    final long armedAt = 0L;
    final long fuseMs = 100L;
    final long now = fuseMs * 1_000_000L;
    assertTrue(ProximityFuseSystem.fuseElapsed(armedAt, now, fuseMs));
  }

  @Test
  public void fuseElapsed_pastFuseExpiry_isTrue() {
    final long armedAt = 0L;
    // 200ms after arming, fuse=100ms → well past.
    final long now = 200L * 1_000_000L;
    assertTrue(ProximityFuseSystem.fuseElapsed(armedAt, now, 100L));
  }

  @Test
  public void fuseElapsed_zeroFuse_alwaysElapsed() {
    // Edge: a fuseMs of 0 means detonate next tick after arming. Realistic
    // operator-authored values are > 0 (createProjectileBomb gates on
    // explodeDelayMs > 0 before stamping ProximityFuse), but the helper
    // shouldn't crash if a 0 ever sneaks through.
    assertTrue(ProximityFuseSystem.fuseElapsed(123L, 124L, 0L));
    assertTrue(ProximityFuseSystem.fuseElapsed(0L, 0L, 0L));
  }
}
