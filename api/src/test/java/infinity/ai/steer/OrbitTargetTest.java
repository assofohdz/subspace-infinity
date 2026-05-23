// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;
import java.util.List;
import org.junit.Test;

public class OrbitTargetTest {

  private static final PerceptionSnapshot EMPTY =
      new PerceptionSnapshot(List.of(), List.of(), List.of());

  private static final double ORBIT_RADIUS = 10.0;
  private static final double THRUST = 1.0;

  @Test
  public void noTargetReturnsNull() {
    final OrbitTarget orbit = new OrbitTarget(ORBIT_RADIUS, THRUST);
    assertNull(orbit.steer(atOrigin(), EMPTY));
  }

  @Test
  public void onTargetPositionReturnsZeroYawForwardThrust() {
    final OrbitTarget orbit = new OrbitTarget(ORBIT_RADIUS, THRUST);
    orbit.setTarget(targetAt(0, 0));
    final Vec3d intent = orbit.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    assertEquals(0.0, intent.x, 1e-9);
    assertEquals(THRUST, intent.z, 1e-9);
  }

  @Test
  public void onOrbitCircleSteersTangentially() {
    // Target at +Z, orbit radius matches distance → pure tangent (no radial correction).
    // tangent formula = (-radial.z, 0, +radial.x) — for radial=+Z that's -X (CCW from above).
    // Bot at identity orientation: body left = +X. yaw = left·tangent = +X·(-X) = -1.
    // Negative yaw = turn right = nose rotates CCW around the target as viewed from above.
    final OrbitTarget orbit = new OrbitTarget(ORBIT_RADIUS, THRUST);
    orbit.setTarget(targetAt(0, ORBIT_RADIUS));
    final Vec3d intent = orbit.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    assertTrue("expected negative yaw for CCW orbit, got " + intent.x, intent.x < 0.0);
    assertEquals(THRUST, intent.z, 1e-9);
  }

  @Test
  public void outsideRadiusPullsInward() {
    // Target straight ahead at 2× orbit radius → tangent (-X) + inward pull (+Z radial).
    // Yaw still derives from tangent (negative); radial pull adds nothing to left.dot.
    // Absolute yaw magnitude should be smaller than on-circle case (desired vector tilts
    // away from pure tangent toward the radial direction).
    final OrbitTarget orbit = new OrbitTarget(ORBIT_RADIUS, THRUST);
    orbit.setTarget(targetAt(0, ORBIT_RADIUS * 2));
    final Vec3d intent = orbit.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    assertTrue("outside radius keeps negative yaw (tangent direction unchanged)", intent.x < 0.0);
    final OrbitTarget reference = new OrbitTarget(ORBIT_RADIUS, THRUST);
    reference.setTarget(targetAt(0, ORBIT_RADIUS));
    final Vec3d onCircle = reference.steer(atOrigin(), EMPTY);
    assertTrue(
        "|outside-yaw| < |on-circle-yaw| (radial pull dilutes tangent)",
        Math.abs(intent.x) < Math.abs(onCircle.x));
  }

  @Test
  public void insideRadiusPushesOutward() {
    // Target straight ahead at half orbit radius → tangent (-X) + outward push (-Z radial).
    // Yaw still primarily from tangent (negative); full thrust forward.
    final OrbitTarget orbit = new OrbitTarget(ORBIT_RADIUS, THRUST);
    orbit.setTarget(targetAt(0, ORBIT_RADIUS / 2.0));
    final Vec3d intent = orbit.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    assertTrue("inside-radius yaw stays negative (tangent direction)", intent.x < 0.0);
    assertEquals(THRUST, intent.z, 1e-9);
  }

  private static MoverState atOrigin() {
    return new MoverState(new Vec3d(), new Quatd(), new Vec3d());
  }

  private static NearbyShip targetAt(final double x, final double z) {
    return new NearbyShip(
        new EntityId(99), new Vec3d(x, 0, z), new Quatd(), new Vec3d(), 0);
  }
}
