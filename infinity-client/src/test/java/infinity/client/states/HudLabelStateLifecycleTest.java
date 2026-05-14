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
 * Lifecycle / leak coverage for {@link HudLabelState}: disabled-mode
 * verifies {@code initialize → cleanup} is NPE-safe when {@code onEnable}'s
 * {@code (Main) getApplication()} cast is skipped. The
 * {@code LabelContainer} {@code EntitySet} is only acquired inside
 * {@code onEnable}, so disabled-mode acquires nothing — {@code cleanup}'s
 * {@code labels != null} guard handles the no-onEnable path. See
 * {@code .claude/rules/entity-sets.md}.
 */
public class HudLabelStateLifecycleTest {

  @Test
  public void disabledLifecycle_skipsEnableHook_initAndCleanupAreSafe() {
    final SyntheticApplication app = SyntheticApplication.builder().build();
    final RecordingEntityData ed = new RecordingEntityData();
    final BaseAppStateLifecycleHarness harness = new BaseAppStateLifecycleHarness(app, ed);
    harness.attachDependency(new StubConnectionState(ed));

    harness.runDisabledLifecycle(new HudLabelState());

    assertEquals("Disabled-only path must not acquire any EntitySets",
        0, ed.entitySets().size());
  }

  /** Minimal {@link ConnectionState} substitute — mirrors the existing samples. */
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
