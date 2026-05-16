// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.arena.ArenaId;
import infinity.es.arena.RoundEndPending;
import infinity.es.arena.RoundNumber;
import infinity.es.score.ScoreReset;
import infinity.modules.ArenaModuleSystem.LoadedArena;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

/** Pins {@link ArenaLifecycleDispatcherSystem}'s round-end dispatch. */
public final class ArenaLifecycleDispatcherSystemTest {

  @Test
  public void pendingRoundEnd_firesLifecycle_resetsScores_advancesRound() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaModuleSystem moduleSystem = new ArenaModuleSystem();
    final ArenaLifecycleDispatcherSystem dispatcher = new ArenaLifecycleDispatcherSystem();
    systems.register(EntityData.class, ed);
    systems.register(infinity.settings.ConfigRegistrySystem.class, stubRegistry());
    systems.register(ArenaModuleSystem.class, moduleSystem);
    systems.register(ArenaLifecycleDispatcherSystem.class, dispatcher);
    systems.initialize();
    systems.start();
    try {
      final EntityId arenaEntity = ed.createEntity();
      final ArenaId arenaId = new ArenaId("ffa", arenaEntity);
      ed.setComponent(arenaEntity, arenaId);
      ed.setComponent(arenaEntity, new RoundNumber(3));

      // First tick: ArenaModuleSystem sees the arena and installs an EMPTY
      // module set (no declarations registered in stub registry).
      systems.update();

      // Now overwrite with our spy module set + flag the round-end.
      final SpyModule spy = new SpyModule();
      installModuleSet(moduleSystem, arenaEntity, arenaId, spy);
      ed.setComponent(arenaEntity, new RoundEndPending());

      systems.update();

      assertEquals(List.of("onRoundEnd:3", "onRoundStart:4"), spy.events);
      assertEquals(4, ed.getComponent(arenaEntity, RoundNumber.class).getValue());
      assertNull("dispatcher cleared the marker",
          ed.getComponent(arenaEntity, RoundEndPending.class));
      final ScoreReset reset = ed.getComponent(arenaEntity, ScoreReset.class);
      assertNotNull("dispatcher emitted ScoreReset", reset);
      assertEquals(ScoreReset.Scope.ROUND, reset.scope());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  /** Inserts a LoadedArena into ArenaModuleSystem via reflection — bypasses EntitySet plumbing for the dispatcher's lookup path. */
  private static void installModuleSet(
      final ArenaModuleSystem moduleSystem,
      final EntityId arenaEntity,
      final ArenaId arenaId,
      final SpyModule spy) {
    try {
      final var loadedField = ArenaModuleSystem.class.getDeclaredField("loaded");
      loadedField.setAccessible(true);
      @SuppressWarnings("unchecked")
      final java.util.Map<EntityId, LoadedArena> map =
          (java.util.Map<EntityId, LoadedArena>) loadedField.get(moduleSystem);
      final ArenaModuleSet set =
          new ArenaModuleSet(
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              java.util.List.of((infinity.modules.ScoringModule) spy),
              java.util.List.of(),
              java.util.Map.of());
      map.put(arenaEntity, new LoadedArena(arenaId, set));
    } catch (final ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static infinity.settings.ConfigRegistrySystem stubRegistry() {
    return new infinity.settings.ConfigRegistrySystem();
  }

  /** Records lifecycle calls so the test can assert order + arguments. */
  private static final class SpyModule implements ScoringModule {
    final List<String> events = new ArrayList<>();

    @Override
    public void onRoundEnd(final ArenaId arenaId, final int roundNumber, final infinity.modules.RoundOutcome outcome) {
      events.add("onRoundEnd:" + roundNumber);
    }

    @Override
    public void onRoundStart(final ArenaId arenaId, final int roundNumber) {
      events.add("onRoundStart:" + roundNumber);
    }
  }
}
