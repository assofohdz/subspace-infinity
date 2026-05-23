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
import org.junit.Test;

/** Behavioural tests for {@link Pursue}'s Reynolds lead-prediction math. */
public class PursueTest {

  private static final double DELTA = 1e-6;
  private static final EntityId TARGET_ID = new EntityId(42L);

  @Test
  public void noTargetSetReturnsNull() {
    final Pursue pursue = new Pursue(0.5, 1.0);
    final Vec3d intent = pursue.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);
    assertNull("no target → null (so PrioritySteering falls through)", intent);
  }

  @Test
  public void targetDeadAheadProducesZeroYawFullThrust() {
    final Pursue pursue = new Pursue(0.5, 1.0);
    pursue.setTarget(stationaryTargetAt(0, 0, 10));
    final Vec3d intent = pursue.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);
    assertNotNull(intent);
    assertEquals("dead-ahead target → no yaw", 0.0, intent.x, DELTA);
    assertEquals("dead-ahead target → full thrust", 1.0, intent.z, DELTA);
  }

  @Test
  public void targetToLeftProducesPositiveYaw() {
    // Vec3d.UNIT_X is the "left" axis in the Mythruna/moss convention (mover.orientation
    // .mult(UNIT_X) is the mover's left). PlayerDriver maps positive intent.x → turn left.
    final Pursue pursue = new Pursue(0.5, 1.0);
    pursue.setTarget(stationaryTargetAt(10, 0, 0));
    final Vec3d intent = pursue.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);
    assertNotNull(intent);
    assertEquals("target on +X (left) → yaw +1", 1.0, intent.x, DELTA);
  }

  @Test
  public void targetToRightProducesNegativeYaw() {
    final Pursue pursue = new Pursue(0.5, 1.0);
    pursue.setTarget(stationaryTargetAt(-10, 0, 0));
    final Vec3d intent = pursue.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);
    assertNotNull(intent);
    assertEquals("target on -X (right) → yaw -1", -1.0, intent.x, DELTA);
  }

  @Test
  public void targetBehindForcesFullTurn() {
    // Target directly behind (along -Z forward direction) — pursue should max the yaw
    // signal so the ship swings around. Sign is deterministic (positive) when delta has
    // no lateral component.
    final Pursue pursue = new Pursue(0.5, 1.0);
    pursue.setTarget(stationaryTargetAt(0, 0, -10));
    final Vec3d intent = pursue.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);
    assertNotNull(intent);
    assertEquals("dead-behind → full-magnitude yaw", 1.0, Math.abs(intent.x), DELTA);
  }

  @Test
  public void leadPredictionAimsAheadOfMovingTarget() {
    // Target at (0,0,10) moving +X at 5 units/sec; leadTime=0.5 → predicted at (2.5,0,10).
    // Mover at origin facing +Z. The predicted point is up-and-to-the-left, so yaw
    // should be positive but smaller than 1 (not a full turn).
    final Pursue pursue = new Pursue(0.5, 1.0);
    pursue.setTarget(targetAt(0, 0, 10, /* vx */ 5, /* vy */ 0, /* vz */ 0));
    final Vec3d intent = pursue.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);
    assertNotNull(intent);
    assertTrue("lead-predicted target shifts yaw toward future position", intent.x > 0.0);
    assertTrue("lead-prediction yaw stays below full-magnitude", intent.x < 1.0);
  }

  @Test
  public void leadPredictionWithZeroVelocityMatchesStationary() {
    // Sanity check — a moving target with zero velocity should produce the same intent
    // as a stationary target at the same position.
    final Pursue moving = new Pursue(0.5, 1.0);
    moving.setTarget(targetAt(0, 0, 10, 0, 0, 0));
    final Vec3d movingIntent = moving.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);

    final Pursue stationary = new Pursue(0.5, 1.0);
    stationary.setTarget(stationaryTargetAt(0, 0, 10));
    final Vec3d stationaryIntent = stationary.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);

    assertEquals(movingIntent.x, stationaryIntent.x, DELTA);
    assertEquals(movingIntent.z, stationaryIntent.z, DELTA);
  }

  private static MoverState mover(final double x, final double y, final double z) {
    return new MoverState(new Vec3d(x, y, z), new Quatd(), new Vec3d());
  }

  private static NearbyShip stationaryTargetAt(final double x, final double y, final double z) {
    return targetAt(x, y, z, 0.0, 0.0, 0.0);
  }

  private static NearbyShip targetAt(
      final double x,
      final double y,
      final double z,
      final double vx,
      final double vy,
      final double vz) {
    return new NearbyShip(
        TARGET_ID, new Vec3d(x, y, z), new Quatd(), new Vec3d(vx, vy, vz), /* freq */ 99);
  }
}
