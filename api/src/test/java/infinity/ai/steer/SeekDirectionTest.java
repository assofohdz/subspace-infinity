// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.PerceptionSnapshot;
import org.junit.Test;

public class SeekDirectionTest {

  private static final double EPS = 1e-6;
  // Bot at origin, identity orientation = facing +Z.
  private static final MoverSnapshot SELF = new MoverSnapshot(new Vec3d(), new Quatd(), new Vec3d());

  @Test
  public void noHeadingReturnsNull() {
    final SeekDirection s = new SeekDirection(1.0);
    assertNull(s.steer(SELF, PerceptionSnapshot.EMPTY));
  }

  @Test
  public void zeroHeadingReturnsNull() {
    final SeekDirection s = new SeekDirection(1.0);
    s.setDesiredDirection(new Vec3d(0, 0, 0));
    assertNull(s.steer(SELF, PerceptionSnapshot.EMPTY));
  }

  @Test
  public void deadAheadIsStraightThrust() {
    final SeekDirection s = new SeekDirection(0.8);
    s.setDesiredDirection(new Vec3d(0, 0, 1));
    final Vec3d out = s.steer(SELF, PerceptionSnapshot.EMPTY);
    assertEquals(0.0, out.x, EPS);
    assertEquals(0.8, out.z, EPS);
  }

  @Test
  public void headingToTheSideTurns() {
    final SeekDirection s = new SeekDirection(1.0);
    s.setDesiredDirection(new Vec3d(1, 0, 0));
    final double rightYaw = s.steer(SELF, PerceptionSnapshot.EMPTY).x;
    s.setDesiredDirection(new Vec3d(-1, 0, 0));
    final double leftYaw = s.steer(SELF, PerceptionSnapshot.EMPTY).x;
    // Opposite sides produce opposite-sign turn signals.
    assertTrue(rightYaw * leftYaw < 0);
    assertEquals(1.0, Math.abs(rightYaw), EPS);
  }

  @Test
  public void headingBehindForcesMaxTurn() {
    final SeekDirection s = new SeekDirection(1.0);
    s.setDesiredDirection(new Vec3d(0, 0, -1));
    final Vec3d out = s.steer(SELF, PerceptionSnapshot.EMPTY);
    assertEquals(1.0, Math.abs(out.x), EPS);
  }

  @Test
  public void sideHeadingGivesNoThrust() {
    // 90deg off (heading to the side) ⇒ pure turn, no forward thrust ("turn, then burn").
    final SeekDirection s = new SeekDirection(1.0);
    s.setDesiredDirection(new Vec3d(1, 0, 0));
    assertEquals(0.0, s.steer(SELF, PerceptionSnapshot.EMPTY).z, EPS);
  }

  @Test
  public void behindGivesNoThrust() {
    final SeekDirection s = new SeekDirection(1.0);
    s.setDesiredDirection(new Vec3d(0, 0, -1));
    assertEquals(0.0, s.steer(SELF, PerceptionSnapshot.EMPTY).z, EPS);
  }

  @Test
  public void partialAlignmentScalesThrust() {
    // 45deg off ⇒ thrust scaled by cos(45) ≈ 0.707.
    final SeekDirection s = new SeekDirection(1.0);
    s.setDesiredDirection(new Vec3d(1, 0, 1));
    assertEquals(Math.sqrt(0.5), s.steer(SELF, PerceptionSnapshot.EMPTY).z, 1e-3);
  }
}
