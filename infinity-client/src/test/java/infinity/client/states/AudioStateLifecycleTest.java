// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.jme3.app.Application;
import com.simsilica.es.EntityData;
import com.simsilica.ethereal.TimeSource;
import infinity.client.ConnectionState;
import infinity.client.audio.AudioState;
import infinity.client.audio.SIAudioFactory;
import infinity.client.test.BaseAppStateLifecycleHarness;
import infinity.client.test.RecordingEntityFixtures.RecordingEntityData;
import infinity.client.test.SyntheticApplication;
import org.junit.Test;

/**
 * Lifecycle / leak coverage for {@link AudioState}'s no-audio early-exit path.
 * Without an {@link com.jme3.audio.AudioRenderer} the state self-disables in
 * {@code initialize}; {@code cleanup} must not throw and must not leak any
 * {@link com.simsilica.es.EntitySet}. See {@code .claude/rules/entity-sets.md}.
 */
public class AudioStateLifecycleTest {

  @Test
  public void noAudioRenderer_initSelfDisables_andCleanupIsSafe() {
    // SyntheticApplication's default audioRenderer == null exercises the
    // AudioState early-exit branch. The state should setEnabled(false) inside
    // initialize() so onEnable/onDisable never run.
    final SyntheticApplication app = SyntheticApplication.builder().build();
    final RecordingEntityData ed = new RecordingEntityData();
    final BaseAppStateLifecycleHarness harness = new BaseAppStateLifecycleHarness(app, ed);
    harness.attachDependency(new StubConnectionState(ed));

    final AudioState state = new AudioState(new SIAudioFactory());
    harness.runFullLifecycle(state, 0);

    assertFalse("AudioState must self-disable when no audio renderer is present",
        state.isEnabled());
    assertEquals("No EntitySets should be acquired on the no-audio path",
        0, ed.entitySets().size());
  }

  /** Same stub shape as {@link MapStateLifecycleTest}; intentionally duplicated to keep the harness fixture-light. */
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
