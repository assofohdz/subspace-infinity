// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;
import infinity.ai.bt.Status;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class InWeaponRangeTest {

  private static final PerceptionSnapshot EMPTY =
      new PerceptionSnapshot(List.of(), List.of(), List.of());
  private static final double RANGE = 10.0;

  private Blackboard blackboard;
  private InWeaponRange condition;

  @Before
  public void setUp() {
    blackboard =
        new Blackboard(
            new Pursue(0.5, 1.0),
            new Wander(1, 2, 0.5, 1.0),
            new OrbitTarget(15, 1.0),
            new Evade(0.5, 1.0));
    blackboard.setSelf(new MoverSnapshot(new Vec3d(), new Quatd(), new Vec3d()));
    blackboard.setPerception(EMPTY);
    condition = new InWeaponRange(RANGE);
  }

  @Test
  public void noTargetReturnsFailure() {
    blackboard.setTarget(null);
    assertEquals(Status.FAILURE, condition.tick(blackboard));
  }

  @Test
  public void targetWithinRangeReturnsSuccess() {
    blackboard.setTarget(targetAt(5, 0));
    assertEquals(Status.SUCCESS, condition.tick(blackboard));
  }

  @Test
  public void targetAtRangeBoundaryReturnsSuccess() {
    blackboard.setTarget(targetAt(RANGE, 0));
    assertEquals(Status.SUCCESS, condition.tick(blackboard));
  }

  @Test
  public void targetBeyondRangeReturnsFailure() {
    blackboard.setTarget(targetAt(RANGE + 0.1, 0));
    assertEquals(Status.FAILURE, condition.tick(blackboard));
  }

  private static NearbyShip targetAt(final double x, final double z) {
    return new NearbyShip(
        new EntityId(99), new Vec3d(x, 0, z), new Quatd(), new Vec3d(), 0);
  }
}
