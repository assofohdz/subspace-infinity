// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import static org.junit.Assert.assertEquals;

import com.jme3.app.Application;
import com.simsilica.es.EntityData;
import com.simsilica.ethereal.TimeSource;
import infinity.client.ConnectionState;
import infinity.client.test.BaseAppStateLifecycleHarness;
import infinity.client.test.RecordingEntityFixtures.RecordingEntityData;
import infinity.client.test.SyntheticApplication;
import org.junit.Test;

/**
 * Lifecycle / leak coverage for {@link MapState}: full
 * {@code initialize → onEnable → onDisable → cleanup} run plus the
 * disabled-at-init shape that the entity-sets rule warns against. See
 * {@code .claude/rules/entity-sets.md}.
 */
public class MapStateLifecycleTest {

  @Test
  public void fullLifecycle_initEnableDisableCleanup_releasesEverySet() {
    final SyntheticApplication app = SyntheticApplication.builder().build();
    final RecordingEntityData ed = new RecordingEntityData();
    final BaseAppStateLifecycleHarness harness = new BaseAppStateLifecycleHarness(app, ed);
    harness.attachDependency(new StubConnectionState(ed));

    final MapState state = new MapState();
    // updateTicks=1 so that update() runs once between onEnable + onDisable —
    // exercises the tileImages.update() drain shape.
    harness.runFullLifecycle(state, 1);

    // MapState's onEnable creates LegacyMapImageContainer (one EntitySet);
    // both onDisable and cleanup() stop it (cleanup is null-safe).
    assertEquals("MapState should have acquired exactly one EntitySet (LegacyMapImageContainer)",
        1, ed.entitySets().size());
  }

  @Test
  public void disabledLifecycle_skipsEnableHook_cleanupRemainsSafe() {
    // BaseAppState contract permits cleanup() to run while disabled — onEnable
    // never fired, so MapState's tileImages is never created. cleanup() must
    // still tolerate the null and not throw.
    final SyntheticApplication app = SyntheticApplication.builder().build();
    final RecordingEntityData ed = new RecordingEntityData();
    final BaseAppStateLifecycleHarness harness = new BaseAppStateLifecycleHarness(app, ed);
    harness.attachDependency(new StubConnectionState(ed));

    harness.runDisabledLifecycle(new MapState());

    assertEquals("Disabled-only path must not acquire any EntitySets",
        0, ed.entitySets().size());
  }

  /**
   * Minimal {@link ConnectionState} substitute — overrides the two accessors
   * touched by the leak-fixed states ({@code getEntityData} +
   * {@code getRemoteTimeSource}) and no-ops the base lifecycle so no
   * Connector thread starts.
   */
  private static final class StubConnectionState extends ConnectionState {
    private final EntityData ed;

    StubConnectionState(final EntityData ed) {
      super(null, "test", 0);
      this.ed = ed;
    }

    @Override public EntityData getEntityData() {
      return ed;
    }
    @Override public TimeSource getRemoteTimeSource() {
      return () -> 0L;
    }
    @Override protected void initialize(final Application app) { /* no-op */ }
    @Override protected void cleanup(final Application app) { /* no-op */ }
    @Override protected void onEnable() { /* no-op */ }
    @Override protected void onDisable() { /* no-op */ }
  }
}
