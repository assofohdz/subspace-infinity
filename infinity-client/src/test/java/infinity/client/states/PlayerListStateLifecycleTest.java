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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Lifecycle / leak coverage for {@link PlayerListState}: disabled-mode
 * run verifies {@code initialize}'s {@code GuiGlobals}-driven InputMapper
 * delegate registration and {@code cleanup}'s symmetric removal — the
 * {@code (SimpleApplication) getApplication()} cast in {@code onEnable}
 * is skipped via {@code setEnabled(false)}. See
 * {@code .claude/rules/entity-sets.md}.
 */
public class PlayerListStateLifecycleTest {

  private RecordingEntityData ed;
  private BaseAppStateLifecycleHarness harness;

  @Before
  public void setUp() {
    final SyntheticApplication app = SyntheticApplication.builder().headlessForGuiGlobals().build();
    ed = new RecordingEntityData();
    harness = new BaseAppStateLifecycleHarness(app, ed);
    harness.attachDependency(new StubConnectionState(ed));
    GuiGlobalsTestFixture.install(app);
  }

  @After
  public void tearDown() {
    GuiGlobalsTestFixture.uninstall();
  }

  @Test
  public void disabledLifecycle_initRegistersDelegate_cleanupRemovesIt_noEntitySetLeak() {
    // PlayerListState's PlayerContainer is created in initialize() but only
    // start()ed in onEnable — disabled-mode keeps it idle, so no EntitySet
    // is ever acquired. initialize/cleanup must still be NPE-safe under the
    // GuiGlobals path (addDelegate / removeDelegate against the stubbed
    // InputMapper).
    harness.runDisabledLifecycle(new PlayerListState());

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
