// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.jme3.app.state.AppState;
import com.jme3.app.state.BaseAppState;
import infinity.client.test.RecordingEntityFixtures.RecordingEntityData;
import infinity.client.test.RecordingEntityFixtures.RecordingEntitySet;
import infinity.client.test.RecordingEntityFixtures.RecordingWatchedEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives a {@link BaseAppState} through {@code initialize → onEnable? → onDisable? → cleanup}
 * against a {@link SyntheticApplication} and asserts the leak-discipline rule from
 * {@code .claude/rules/entity-sets.md} (every {@link RecordingEntitySet} /
 * {@link RecordingWatchedEntity} the state acquired must be released by the
 * time {@code cleanup()} returns).
 */
public final class BaseAppStateLifecycleHarness {

  private final SyntheticApplication app;
  private final RecordingEntityData entityData;
  private final List<AppState> dependencies = new ArrayList<>();

  /** New harness backed by the supplied application + recording entity data. */
  public BaseAppStateLifecycleHarness(final SyntheticApplication app,
                                      final RecordingEntityData entityData) {
    this.app = app;
    this.entityData = entityData;
  }

  /** Convenience: default {@link SyntheticApplication}. */
  public static BaseAppStateLifecycleHarness withDefaults() {
    return new BaseAppStateLifecycleHarness(SyntheticApplication.builder().build(),
        new RecordingEntityData());
  }

  public SyntheticApplication application() { return app; }

  public RecordingEntityData entityData() { return entityData; }

  /** Attach dependency states (e.g. a stubbed {@code ConnectionState}) before the state under test. */
  public BaseAppStateLifecycleHarness attachDependency(final AppState state) {
    app.getStateManager().attach(state);
    dependencies.add(state);
    return this;
  }

  /**
   * Runs the full lifecycle: initialize → optional ticks → cleanup. Asserts no
   * exceptions and that every recorded {@link RecordingEntitySet} /
   * {@link RecordingWatchedEntity} has been released. Detaches all dependencies
   * after the run.
   */
  public void runFullLifecycle(final BaseAppState stateUnderTest, final int updateTicks) {
    try {
      app.getStateManager().attach(stateUnderTest);
      // attach() schedules; update() drains the initialization queue and
      // dispatches initialize → onEnable. One update is required even when
      // updateTicks == 0 to make initialize() actually run.
      app.getStateManager().update(0.016f);
      for (int i = 0; i < updateTicks; i++) {
        app.getStateManager().update(0.016f);
      }
      app.getStateManager().detach(stateUnderTest);
      // Drain the terminating queue so cleanup() actually runs.
      app.getStateManager().update(0.016f);
    } finally {
      for (final AppState dep : dependencies) {
        if (dep.isInitialized()) {
          app.getStateManager().detach(dep);
        }
      }
      app.getStateManager().update(0.016f);
    }
    assertLeakDiscipline();
  }

  @SuppressWarnings("PMD.LooseCoupling") // RecordingEntitySet's releaseCount() is the leak-assertion surface
  private void assertLeakDiscipline() {
    int idx = 0;
    for (final RecordingEntitySet rs : entityData.entitySets()) {
      assertNotNull("EntitySet " + idx + " must not be null", rs);
      assertEquals("EntitySet " + idx + " must be released exactly once after cleanup",
          1, rs.releaseCount());
      idx++;
    }
    idx = 0;
    for (final RecordingWatchedEntity rw : entityData.watchedEntities()) {
      assertNotNull("WatchedEntity " + idx + " must not be null", rw);
      assertEquals("WatchedEntity " + idx + " must be released exactly once after cleanup",
          1, rw.releaseCount());
      idx++;
    }
  }

  /**
   * Drives the lifecycle with the state initially {@code disabled} so
   * {@code onEnable}/{@code onDisable} never run — verifies a state's
   * {@code initialize}/{@code cleanup} are safe even when the
   * disable/enable hooks are skipped (the contract that makes
   * {@code release-in-cleanup} the right rule per
   * {@code .claude/rules/entity-sets.md}).
   */
  public void runDisabledLifecycle(final BaseAppState stateUnderTest) {
    stateUnderTest.setEnabled(false);
    runFullLifecycle(stateUnderTest, 0);
  }
}
