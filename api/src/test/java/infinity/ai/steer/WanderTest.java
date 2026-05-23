// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.PerceptionSnapshot;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.Test;

public class WanderTest {

  private static final PerceptionSnapshot EMPTY =
      new PerceptionSnapshot(List.of(), List.of(), List.of());

  private static MoverState atOrigin() {
    return new MoverState(new Vec3d(), new Quatd(), new Vec3d());
  }

  @Test
  public void alwaysReturnsNonNullIntentWithFullThrust() {
    final Wander wander = new Wander(1.0, 2.0, 0.5, 1.0, fixed(0.5));
    final Vec3d intent = wander.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    assertEquals("thrust is constant", 1.0, intent.z, 1e-9);
  }

  @Test
  public void yawIsBoundedInUnitRange() {
    final Wander wander = new Wander(1.0, 2.0, 0.5, 1.0, fixed(1.0));
    for (int i = 0; i < 200; i++) {
      final Vec3d intent = wander.steer(atOrigin(), EMPTY);
      assertTrue("yaw bounded: " + intent.x, intent.x >= -1.0 && intent.x <= 1.0);
    }
  }

  @Test
  public void angleDriftsByJitterEachTick() {
    // nextDouble returns 1.0 → drift is (1*2-1)*jitter = +jitter per tick.
    final Wander wander = new Wander(1.0, 2.0, 0.5, 1.0, fixed(1.0));
    wander.steer(atOrigin(), EMPTY);
    assertEquals(0.5, wander.currentAngleRadians(), 1e-9);
    wander.steer(atOrigin(), EMPTY);
    assertEquals(1.0, wander.currentAngleRadians(), 1e-9);
  }

  @Test
  public void angleStaysCenteredWhenJitterCancels() {
    // nextDouble = 0.5 → drift is (0.5*2-1)*jitter = 0 per tick.
    final Wander wander = new Wander(1.0, 2.0, 0.5, 1.0, fixed(0.5));
    for (int i = 0; i < 10; i++) {
      wander.steer(atOrigin(), EMPTY);
    }
    assertEquals(0.0, wander.currentAngleRadians(), 1e-9);
  }

  private static RandomGenerator fixed(final double value) {
    return new RandomGenerator() {
      @Override
      public long nextLong() {
        return 0L;
      }

      @Override
      public double nextDouble() {
        return value;
      }
    };
  }
}
