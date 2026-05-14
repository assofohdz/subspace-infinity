// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import static org.junit.Assert.assertEquals;

import com.jme3.app.Application;
import com.simsilica.es.EntityData;
import com.simsilica.ethereal.TimeSource;
import infinity.client.ConnectionState;
import infinity.client.test.BaseAppStateLifecycleHarness;
import infinity.client.test.GuiGlobalsTestFixture;
import infinity.client.test.RecordingEntityFixtures.RecordingEntityData;
import infinity.client.test.SyntheticApplication;
import infinity.client.view.SpeechViewState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Lifecycle / leak coverage for {@link SpeechViewState}: disabled-mode
 * verifies {@code initialize}'s {@code GuiGlobals.loadFont(...)} call
 * resolves against the real headless asset manager + that
 * {@code cleanup} is null-safe when {@code onEnable}'s
 * {@code (SimpleApplication) getApplication()} cast and
 * {@code SpeechContainer} start are skipped. See
 * {@code .claude/rules/entity-sets.md}.
 */
public class SpeechViewStateLifecycleTest {

  private RecordingEntityData ed;
  private BaseAppStateLifecycleHarness harness;

  @Before
  public void setUp() {
    final SyntheticApplication app = SyntheticApplication.builder().headlessForGuiGlobals().build();
    ed = new RecordingEntityData();
    harness = new BaseAppStateLifecycleHarness(app, ed);
    harness.attachDependency(new StubConnectionState(ed));
    harness.attachDependency(new StubModelViewState());
    GuiGlobalsTestFixture.install(app);
  }

  @After
  public void tearDown() {
    GuiGlobalsTestFixture.uninstall();
  }

  @Test
  public void disabledLifecycle_initLoadsFont_cleanupIsSafeWithoutSpeechContainer() {
    // SpeechContainer is constructed inside onEnable; disabled-mode keeps
    // it null, so no EntitySet is acquired. cleanup()'s `bubbles != null`
    // guard handles the no-onEnable path.
    harness.runDisabledLifecycle(new SpeechViewState());

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

  /**
   * Stub {@link ModelViewState} — {@code SpeechViewState.initialize} calls
   * {@code getState(ModelViewState.class, true)}, which fails if no instance
   * is attached. This stub no-ops its own lifecycle + {@code update} so no
   * cascading state dependencies (notably {@code LocalViewState}) are required.
   */
  private static final class StubModelViewState extends ModelViewState {
    @Override protected void initialize(final Application app) { /* no-op */ }
    @Override protected void cleanup(final Application app) { /* no-op */ }
    @Override protected void onEnable() { /* no-op */ }
    @Override protected void onDisable() { /* no-op */ }
    @Override public void update(final float tpf) { /* no-op */ }
  }
}
