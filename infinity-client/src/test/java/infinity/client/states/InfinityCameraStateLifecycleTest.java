// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import static org.junit.Assert.assertEquals;

import com.jme3.app.Application;
import com.jme3.network.service.ClientService;
import com.simsilica.es.EntityData;
import com.simsilica.ethereal.TimeSource;
import infinity.client.ConnectionState;
import infinity.client.test.BaseAppStateLifecycleHarness;
import infinity.client.test.RecordingEntityFixtures.RecordingEntityData;
import infinity.client.test.SyntheticApplication;
import org.junit.Test;

/**
 * Lifecycle / leak coverage for {@link InfinityCameraState}. After the P4 player-vs-ship
 * identity split, the camera state acquires its {@code BodyPosition} {@code WatchedEntity}
 * lazily in {@code update()} (against the player's current ship id via
 * {@code GameSessionState.getCurrentShipId()}), not in {@code initialize()}. The leak
 * surface this test guards has shifted: {@code initialize()} + {@code cleanup()} alone
 * MUST NOT acquire or leak anything, since update() is the bind site.
 * See {@code .claude/rules/entity-sets.md}.
 */
public class InfinityCameraStateLifecycleTest {

  @Test
  public void initThenCleanup_acquiresNoWatcher_withoutDrivingUpdate() {
    final SyntheticApplication app = SyntheticApplication.builder().build();
    final RecordingEntityData ed = new RecordingEntityData();
    final BaseAppStateLifecycleHarness harness = new BaseAppStateLifecycleHarness(app, ed);
    harness.attachDependency(new StubConnectionState(ed));

    final InfinityCameraState state = new InfinityCameraState(() -> 0L);
    harness.runDisabledLifecycle(state);

    assertEquals(
        "InfinityCameraState must not acquire a WatchedEntity in initialize() — binding is lazy",
        0, ed.watchedEntities().size());
  }

  /** Returns null for any service — the camera state's session field stays null but cleanup never reads it. */
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
    @Override public <T extends ClientService> T getService(final Class<T> type) {
      return null;
    }
    @Override protected void initialize(final Application app) { /* no-op */ }
    @Override protected void cleanup(final Application app) { /* no-op */ }
    @Override protected void onEnable() { /* no-op */ }
    @Override protected void onDisable() { /* no-op */ }
  }
}
