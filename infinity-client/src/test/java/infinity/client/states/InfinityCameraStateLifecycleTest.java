// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.jme3.app.Application;
import com.jme3.network.service.ClientService;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ethereal.TimeSource;
import infinity.client.ConnectionState;
import infinity.client.test.BaseAppStateLifecycleHarness;
import infinity.client.test.RecordingEntityFixtures.RecordingEntityData;
import infinity.client.test.RecordingEntityFixtures.RecordingWatchedEntity;
import infinity.client.test.SyntheticApplication;
import org.junit.Test;

/**
 * Lifecycle / leak coverage for {@link InfinityCameraState} — the
 * canonical {@code WatchedEntity} leak shape from the 7-leak sweep
 * (avatar body-position watch acquired in {@code initialize}, released
 * in {@code cleanup}). See {@code .claude/rules/entity-sets.md}.
 *
 * <p>Driven with {@code setEnabled(false)} so {@code onEnable}'s
 * {@code GuiGlobals.getInstance()} dependency stays out of the test surface;
 * the WatchedEntity is acquired in {@code initialize} regardless.
 */
public class InfinityCameraStateLifecycleTest {

  @Test
  public void disabledLifecycle_acquiresWatchedEntityInInit_releasesItInCleanup() {
    final SyntheticApplication app = SyntheticApplication.builder().build();
    final RecordingEntityData ed = new RecordingEntityData();
    final BaseAppStateLifecycleHarness harness = new BaseAppStateLifecycleHarness(app, ed);
    harness.attachDependency(new StubConnectionState(ed));

    final EntityId avatar = ed.createEntity();
    final InfinityCameraState state = new InfinityCameraState(avatar, () -> 0L);
    harness.runDisabledLifecycle(state);

    assertEquals("InfinityCameraState should have acquired exactly one WatchedEntity",
        1, ed.watchedEntities().size());
    final RecordingWatchedEntity self = ed.watchedEntities().get(0);
    assertTrue("The avatar WatchedEntity must have been released by cleanup()",
        self.wasReleased());
    assertEquals("And exactly once", 1, self.releaseCount());
  }

  /** Returns null for any service — the camera state's session field stays null but cleanup never reads it. */
  private static final class StubConnectionState extends ConnectionState {
    private final EntityData ed;

    StubConnectionState(final EntityData ed) {
      super(null, "test", 0);
      this.ed = ed;
    }

    @Override public EntityData getEntityData() { return ed; }
    @Override public TimeSource getRemoteTimeSource() { return () -> 0L; }
    @Override public <T extends ClientService> T getService(final Class<T> type) { return null; }
    @Override protected void initialize(final Application app) { /* no-op */ }
    @Override protected void cleanup(final Application app) { /* no-op */ }
    @Override protected void onEnable() { /* no-op */ }
    @Override protected void onDisable() { /* no-op */ }
  }
}
