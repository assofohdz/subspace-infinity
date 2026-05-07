// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import org.junit.Test;

/**
 * Pins the Slice S2 static helper {@link WeaponsSystem#recoilImpulse} that
 * drives bomb-fire recoil. Pure-function tests cover direction, magnitude,
 * sign-preservation, and zero-thrust pass-through without bringing up an
 * ECS / physics fixture.
 *
 * <p>The system-level integration ({@code applyBombRecoil} reading
 * {@code BombThrust} component, looking up the body via mphys, and writing
 * {@code Impulse} on the entity) is deferred to a heavier integration test.
 */
public class WeaponsSystemRecoilTest {

  /** EngineConfig DEFAULTS — keep the math fit-able to authored values. */
  private static final double SCALE = 0.01;

  private static final double MAX = 100.0;

  private static final double EPSILON = 1.0e-9;

  // -----------------------------------------------------------------
  // Direction — opposite of ship's bodyForward in world space
  // -----------------------------------------------------------------

  @Test
  public void direction_shipFacingPositiveZ_recoilPointsNegativeZ() {
    // Identity orientation = forward is +Z. Recoil should push -Z.
    final Vec3d r = WeaponsSystem.recoilImpulse(400, SCALE, MAX, new Quatd());
    assertEquals("X component", 0.0, r.x, EPSILON);
    assertEquals("Y component", 0.0, r.y, EPSILON);
    assertEquals("Z component (recoil)", -4.0, r.z, EPSILON); // 400 × 0.01 × -1
  }

  @Test
  public void direction_shipFacingPositiveX_recoilPointsNegativeX() {
    // 90° yaw → forward is +X. Recoil should push -X.
    final Quatd yaw90 = new Quatd().fromAngles(0, Math.PI / 2.0, 0);
    final Vec3d r = WeaponsSystem.recoilImpulse(400, SCALE, MAX, yaw90);
    assertEquals("X component (recoil)", -4.0, r.x, EPSILON);
    assertEquals("Y component", 0.0, r.y, EPSILON);
    assertEquals("Z component", 0.0, r.z, EPSILON);
  }

  // -----------------------------------------------------------------
  // Magnitude — uses effectiveProjectileSpeed (scale × cap)
  // -----------------------------------------------------------------

  @Test
  public void magnitude_subspaceCanon_400_at_scale_0_01() {
    final Vec3d r = WeaponsSystem.recoilImpulse(400, 0.01, 100.0, new Quatd());
    assertEquals(4.0, r.length(), EPSILON);
  }

  @Test
  public void magnitude_capsAtMaxJme_whenScaledExceeds() {
    // 64636 * 0.01 = 646.36 → clamped to 100.
    final Vec3d r = WeaponsSystem.recoilImpulse(64636, 0.01, 100.0, new Quatd());
    assertEquals(100.0, r.length(), EPSILON);
  }

  // -----------------------------------------------------------------
  // Zero / sign behaviours
  // -----------------------------------------------------------------

  @Test
  public void zero_thrust_returnsZeroVector() {
    final Vec3d r = WeaponsSystem.recoilImpulse(0, SCALE, MAX, new Quatd());
    assertEquals(Vec3d.ZERO, r);
  }

  @Test
  public void negative_thrust_pushesShipForward() {
    // Negative thrust = forward push (parallels slice 10b's signed projectile
    // speed). Sign is preserved through effectiveProjectileSpeed and then
    // multiplied by -1, landing as +Z when ship faces +Z.
    final Vec3d r = WeaponsSystem.recoilImpulse(-400, SCALE, MAX, new Quatd());
    assertEquals(0.0, r.x, EPSILON);
    assertEquals(0.0, r.y, EPSILON);
    assertEquals(4.0, r.z, EPSILON);
  }

  @Test
  public void negative_thrust_capsAtNegativeMaxJme() {
    // -64636 * 0.01 = -646.36 → clamped to -100; final flip → +100 in z.
    final Vec3d r = WeaponsSystem.recoilImpulse(-64636, 0.01, 100.0, new Quatd());
    assertEquals(100.0, r.z, EPSILON);
  }
}
