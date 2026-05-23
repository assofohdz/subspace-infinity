// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.bt;

import static org.junit.Assert.assertEquals;

import infinity.ai.brain.Blackboard;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SequenceTest {

  private final Blackboard blackboard = new Blackboard(new Pursue(0.5, 1.0), new Wander(1, 2, 0.5, 1.0));

  @Test
  public void emptySequenceReturnsSuccess() {
    assertEquals(Status.SUCCESS, new Sequence().tick(blackboard));
  }

  @Test
  public void allChildrenSuccessReturnsSuccess() {
    final Sequence seq = new Sequence(constant(Status.SUCCESS), constant(Status.SUCCESS));
    assertEquals(Status.SUCCESS, seq.tick(blackboard));
  }

  @Test
  public void firstFailureShortCircuits() {
    final List<String> log = new ArrayList<>();
    final Sequence seq = new Sequence(
        recording(log, "a", Status.SUCCESS),
        recording(log, "b", Status.FAILURE),
        recording(log, "c", Status.SUCCESS));
    assertEquals(Status.FAILURE, seq.tick(blackboard));
    assertEquals(List.of("a", "b"), log);
  }

  @Test
  public void runningPropagatesAndShortCircuits() {
    final List<String> log = new ArrayList<>();
    final Sequence seq = new Sequence(
        recording(log, "a", Status.SUCCESS),
        recording(log, "b", Status.RUNNING),
        recording(log, "c", Status.SUCCESS));
    assertEquals(Status.RUNNING, seq.tick(blackboard));
    assertEquals(List.of("a", "b"), log);
  }

  private static Behavior constant(final Status status) {
    return blackboard -> status;
  }

  private static Behavior recording(final List<String> log, final String name, final Status status) {
    return blackboard -> {
      log.add(name);
      return status;
    };
  }
}
