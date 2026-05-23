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

public class SelectorTest {

  private final Blackboard blackboard = new Blackboard(new Pursue(0.5, 1.0), new Wander(1, 2, 0.5, 1.0));

  @Test
  public void emptySelectorReturnsFailure() {
    assertEquals(Status.FAILURE, new Selector().tick(blackboard));
  }

  @Test
  public void allChildrenFailureReturnsFailure() {
    final Selector sel = new Selector(constant(Status.FAILURE), constant(Status.FAILURE));
    assertEquals(Status.FAILURE, sel.tick(blackboard));
  }

  @Test
  public void firstSuccessShortCircuits() {
    final List<String> log = new ArrayList<>();
    final Selector sel = new Selector(
        recording(log, "a", Status.FAILURE),
        recording(log, "b", Status.SUCCESS),
        recording(log, "c", Status.SUCCESS));
    assertEquals(Status.SUCCESS, sel.tick(blackboard));
    assertEquals(List.of("a", "b"), log);
  }

  @Test
  public void runningPropagatesAndShortCircuits() {
    final List<String> log = new ArrayList<>();
    final Selector sel = new Selector(
        recording(log, "a", Status.FAILURE),
        recording(log, "b", Status.RUNNING),
        recording(log, "c", Status.SUCCESS));
    assertEquals(Status.RUNNING, sel.tick(blackboard));
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
