// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityData;
import com.simsilica.mathd.Grid;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.GameSystemManager;
import infinity.ai.MobSystem;
import infinity.client.test.BaseAppStateLifecycleHarness;
import infinity.client.test.GuiGlobalsTestFixture;
import infinity.client.test.RecordingEntityFixtures.RecordingEntityData;
import infinity.client.test.SyntheticApplication;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import sun.misc.Unsafe;

/**
 * Lifecycle / leak coverage for {@link MobDebugState}: full
 * {@code initialize → onEnable → onDisable → cleanup} run with
 * {@code probesEnabled=false} (default) so {@code resetProbesEnabled} no-ops
 * and the {@code (SimpleApplication) getApplication()} cast in
 * {@code getRoot()} is never hit. {@code initialize}'s
 * {@code GuiGlobals.createMaterial(...)} call resolves against the real
 * headless GuiGlobals + asset manager. See {@code .claude/rules/entity-sets.md}.
 */
public class MobDebugStateLifecycleTest {

  private RecordingEntityData ed;
  private BaseAppStateLifecycleHarness harness;
  private HostState host;

  @Before
  public void setUp() throws ReflectiveOperationException {
    final SyntheticApplication app = SyntheticApplication.builder().headlessForGuiGlobals().build();
    ed = new RecordingEntityData();
    harness = new BaseAppStateLifecycleHarness(app, ed);
    GuiGlobalsTestFixture.install(app);
    host = buildStubHostState(ed);
  }

  @After
  public void tearDown() {
    GuiGlobalsTestFixture.uninstall();
  }

  @Test
  public void fullLifecycle_probesDisabled_initEnableCleanupNoEntitySetLeak() {
    // ProbeContainer is constructed in initialize() but only start()ed via
    // resetProbesEnabled() when probesEnabled flips true. Default false →
    // no EntitySet is ever acquired. cleanup()'s `probes != null` guard +
    // `probesStarted` check covers the no-onEnable-side-effect path.
    final MobDebugState state = new MobDebugState(host);
    harness.runFullLifecycle(state, 0);

    assertEquals("MobDebugState with probes disabled must not acquire any EntitySets",
        0, ed.entitySets().size());
  }

  /**
   * Allocate a {@link HostState} via {@link Unsafe} (skip its ctor — the
   * real ctor opens a TCP listener for GameServer) and reflectively seed
   * its {@code systems} field with a populated {@link GameSystemManager}.
   * {@code MobDebugState.initialize} reads three systems off this manager:
   * {@link EntityData}, {@link MobSystem}, {@link PhysicsSpace}.
   */
  private static HostState buildStubHostState(final EntityData ed) throws ReflectiveOperationException {
    final Unsafe unsafe = unsafe();
    final HostState stub = (HostState) unsafe.allocateInstance(HostState.class);
    final GameSystemManager systems = new GameSystemManager();
    systems.register(EntityData.class, ed);
    systems.register(MobSystem.class, new MobSystem());
    systems.register(PhysicsSpace.class, new PhysicsSpace<>(new Grid(32)));
    final Field systemsField = HostState.class.getDeclaredField("systems");
    systemsField.setAccessible(true);
    systemsField.set(stub, systems);
    return stub;
  }

  private static Unsafe unsafe() throws ReflectiveOperationException {
    final Field f = Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return (Unsafe) f.get(null);
  }
}
