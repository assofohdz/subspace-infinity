// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import infinity.ai.field.DistanceField;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

public class AsyncNavigationFieldsTest {

  private static final long TTL = 5_000_000_000L; // 5s
  private static final int MAX_TRANSIENT = 4;

  private ManualExecutor exec;
  private AtomicLong clock;
  private AsyncNavigationFields nav;

  @Before
  public void setUp() {
    exec = new ManualExecutor();
    clock = new AtomicLong(1_000L);
    nav = new AsyncNavigationFields(open(5), exec, clock::get, TTL, MAX_TRANSIENT);
  }

  @Test
  public void getNowReturnsEmptyBeforeBuild_thenRealFieldAfter() {
    assertSame("pre-completion read is the EMPTY sentinel", DistanceField.EMPTY, nav.fieldFor(2, 2));
    exec.runAll(); // worker completes the Dijkstra build
    final DistanceField field = nav.fieldFor(2, 2);
    assertNotSame("after completion, a real field", DistanceField.EMPTY, field);
    assertEquals(0.0, field.valueAt(2, 2), 0.0); // distance 0 at the goal cell
  }

  @Test
  public void pinnedSurvivesTtl_transientEvicted() {
    nav.pin(1, 1);
    nav.fieldFor(3, 3); // transient
    exec.runAll();
    assertEquals(2, nav.size());

    clock.addAndGet(TTL + 1); // both now idle past TTL
    nav.evictExpired();

    assertEquals("pinned static field survives; transient evicted", 1, nav.size());
  }

  @Test
  public void failedBuildIsNonBlocking_andRetriesNextRequest() {
    // Jagged grid (null row) makes the Dijkstra build throw → future completes exceptionally.
    final AsyncNavigationFields broken =
        new AsyncNavigationFields(
            new boolean[][] {{true, true}, null}, exec, clock::get, TTL, MAX_TRANSIENT);
    assertSame(DistanceField.EMPTY, broken.fieldFor(0, 0));
    exec.runAll(); // build fails
    final int afterFirst = exec.submitted;
    // Next request must not throw, returns EMPTY, and queues a rebuild (evict + retry).
    assertSame(DistanceField.EMPTY, broken.fieldFor(0, 0));
    assertTrue("failed future triggers a rebuild attempt", exec.submitted > afterFirst);
  }

  @Test
  public void evictAndEvictAll() {
    nav.fieldFor(1, 1);
    nav.fieldFor(2, 2);
    exec.runAll();
    nav.evict(1, 1);
    assertEquals(1, nav.size());
    nav.evictAll();
    assertEquals(0, nav.size());
  }

  @Test
  public void lruCapDropsLeastRecentlyAccessed() {
    for (int g = 0; g < MAX_TRANSIENT + 2; g++) {
      clock.addAndGet(1000L); // each access newer than the last
      nav.fieldFor(g, 0);
    }
    assertEquals("transient cache bounded by maxTransient", MAX_TRANSIENT, nav.size());
    exec.runAll();
    // The earliest goals (0,0) and (1,0) were least-recently-accessed → evicted.
    assertSame(DistanceField.EMPTY, peek(nav, 0, 0));
  }

  /** Read without mutating access time would be ideal, but fieldFor is the only accessor; good enough. */
  private static DistanceField peek(final AsyncNavigationFields nav, final int x, final int y) {
    return nav.fieldFor(x, y);
  }

  private static boolean[][] open(final int n) {
    final boolean[][] grid = new boolean[n][n];
    for (final boolean[] row : grid) {
      java.util.Arrays.fill(row, true);
    }
    return grid;
  }

  /** Executor that queues tasks until {@link #runAll()}; counts submissions for retry assertions. */
  private static final class ManualExecutor implements Executor {
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private int submitted;

    @Override
    public void execute(final Runnable command) {
      this.tasks.add(command);
      this.submitted++;
    }

    void runAll() {
      while (!this.tasks.isEmpty()) {
        this.tasks.poll().run();
      }
    }
  }
}
