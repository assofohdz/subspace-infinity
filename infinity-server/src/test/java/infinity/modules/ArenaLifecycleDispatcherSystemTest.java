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
import infinity.es.arena.MatchNumber;
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

  @Test
  public void dispatcherAggregatesWinner_firstNonUndecidedWins() {
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
      systems.update();

      final SpyScoring scoringSpy = new SpyScoring();
      final SpyWinCondition abstaining =
          new SpyWinCondition(WinnerDeclaration.UNDECIDED);
      final SpyWinCondition deciding = new SpyWinCondition(new WinnerDeclaration(42, "test"));
      installModuleSetWithWinConditions(
          moduleSystem, arenaEntity, arenaId, scoringSpy, abstaining, deciding);
      ed.setComponent(arenaEntity, new RoundEndPending());

      systems.update();

      assertEquals(
          "dispatcher passes aggregated winningFreq into onRoundEnd's RoundOutcome",
          42,
          scoringSpy.lastOutcome.winningFreq());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  @Test
  public void matchStructureRequestsEnd_firesOnMatchEnd_bumpsMatchNumber_emitsScoreResetMatch() {
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
      ed.setComponent(arenaEntity, new RoundNumber(2));
      ed.setComponent(arenaEntity, new MatchNumber(1));
      systems.update();

      final SpyLifecycle spy = new SpyLifecycle();
      final TerminatingMatch ms = new TerminatingMatch();
      installSet(
          moduleSystem,
          arenaEntity,
          arenaId,
          new ArenaModuleSet(
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ms),
              Optional.empty(),
              Optional.empty(),
              java.util.List.of(spy),
              java.util.List.of(),
              java.util.Map.of()));
      ed.setComponent(arenaEntity, new RoundEndPending());

      systems.update();

      assertEquals(
          "lifecycle order: onRoundEnd → onMatchEnd → onMatchStart → onRoundStart",
          List.of("onRoundEnd:2", "onMatchEnd", "onMatchStart", "onRoundStart:3"),
          spy.events);
      assertEquals("MatchNumber bumped", 2, ed.getComponent(arenaEntity, MatchNumber.class).getValue());
      final ScoreReset reset = ed.getComponent(arenaEntity, ScoreReset.class);
      assertNotNull("dispatcher emitted some ScoreReset", reset);
      // Dispatcher picks the higher-tier scope when match also ends; coordinator
      // semantics treat MATCH as cascade-zeroing both match + round tiers.
      assertEquals(ScoreReset.Scope.MATCH, reset.scope());
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
    installSet(
        moduleSystem,
        arenaEntity,
        arenaId,
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
            java.util.Map.of()));
  }

  private static void installModuleSetWithWinConditions(
      final ArenaModuleSystem moduleSystem,
      final EntityId arenaEntity,
      final ArenaId arenaId,
      final SpyScoring scoring,
      final infinity.modules.WinConditionModule... winConditions) {
    installSet(
        moduleSystem,
        arenaEntity,
        arenaId,
        new ArenaModuleSet(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            java.util.List.of(scoring),
            java.util.List.of(winConditions),
            java.util.Map.of()));
  }

  private static void installSet(
      final ArenaModuleSystem moduleSystem,
      final EntityId arenaEntity,
      final ArenaId arenaId,
      final ArenaModuleSet set) {
    try {
      final var loadedField = ArenaModuleSystem.class.getDeclaredField("loaded");
      loadedField.setAccessible(true);
      @SuppressWarnings("unchecked")
      final java.util.Map<EntityId, LoadedArena> map =
          (java.util.Map<EntityId, LoadedArena>) loadedField.get(moduleSystem);
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

  /** Captures the last {@code onRoundEnd} outcome for winner-aggregation assertions. */
  private static final class SpyScoring implements ScoringModule {
    infinity.modules.RoundOutcome lastOutcome;

    @Override
    public void onRoundEnd(final ArenaId arenaId, final int roundNumber, final infinity.modules.RoundOutcome outcome) {
      this.lastOutcome = outcome;
    }
  }

  /** Returns a canned {@link WinnerDeclaration} from {@code declareWinner}. */
  private static final class SpyWinCondition implements infinity.modules.WinConditionModule {
    private final WinnerDeclaration vote;

    SpyWinCondition(final WinnerDeclaration vote) {
      this.vote = vote;
    }

    @Override
    public WinnerDeclaration declareWinner(final ArenaId arenaId) {
      return vote;
    }
  }

  /** Records the full set of lifecycle calls (round + match) in fire order. */
  private static final class SpyLifecycle implements ScoringModule {
    final List<String> events = new ArrayList<>();

    @Override
    public void onMatchStart(final ArenaId arenaId) {
      events.add("onMatchStart");
    }

    @Override
    public void onRoundStart(final ArenaId arenaId, final int roundNumber) {
      events.add("onRoundStart:" + roundNumber);
    }

    @Override
    public void onRoundEnd(final ArenaId arenaId, final int roundNumber, final infinity.modules.RoundOutcome outcome) {
      events.add("onRoundEnd:" + roundNumber);
    }

    @Override
    public void onMatchEnd(final ArenaId arenaId, final infinity.modules.MatchOutcome outcome) {
      events.add("onMatchEnd");
    }
  }

  /** matchStructure that always declares match-end on round-end. */
  private static final class TerminatingMatch implements infinity.modules.MatchStructureModule {
    @Override
    public boolean shouldMatchEnd(final ArenaId arenaId, final infinity.modules.RoundOutcome outcome) {
      return true;
    }
  }
}
