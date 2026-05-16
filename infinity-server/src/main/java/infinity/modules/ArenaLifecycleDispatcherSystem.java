// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.es.arena.MatchNumber;
import infinity.es.arena.RoundEndPending;
import infinity.es.arena.RoundNumber;
import infinity.es.score.ScoreReset;
import infinity.modules.ArenaModuleSystem.LoadedArena;
import infinity.sim.ArenaModule;
import infinity.systems.BaseInfinitySystem;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase-4 dispatcher per ADR-0008. Watches arena entities for {@link RoundEndPending}
 * markers (set by a {@code roundStructure} or {@code winCondition} terminator); on each:
 * aggregates the {@link WinnerDeclaration}, fires {@code onRoundEnd}, queries the
 * arena's {@code matchStructure} via {@link MatchStructureModule#shouldMatchEnd},
 * runs the match-end cycle ({@code onMatchEnd} + {@link ScoreReset#scope() MATCH}
 * reset + {@code onMatchStart} + {@link MatchNumber} bump) when applicable, then
 * always runs the round-start cycle ({@code ScoreReset(ROUND)} + {@code RoundNumber}
 * bump + {@code onRoundStart}) and clears the marker.
 */
public final class ArenaLifecycleDispatcherSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet pending;
  private ArenaModuleSystem moduleSystem;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    moduleSystem = requireSystem(ArenaModuleSystem.class);
    pending = ed.getEntities(ArenaId.class, RoundEndPending.class);
  }

  @Override
  protected void terminate() {
    pending.release();
    pending = null;
  }

  @Override
  public void update(final SimTime time) {
    pending.applyChanges();
    for (final Entity arenaEntity : pending.getAddedEntities()) {
      dispatch(arenaEntity);
    }
  }

  private void dispatch(final Entity arenaEntity) {
    final EntityId arenaEntityId = arenaEntity.getId();
    final LoadedArena entry = moduleSystem.loadedFor(arenaEntityId);
    if (entry == null) {
      ed.removeComponent(arenaEntityId, RoundEndPending.class);
      return;
    }
    final ArenaId arenaId = entry.arenaId();
    final ArenaModuleSet set = entry.set();
    final List<ArenaModule> modules = set.allModules();

    final int finishedRound = currentRound(arenaEntityId);
    final RoundOutcome roundOutcome = buildRoundOutcome(set, arenaId);
    for (final ArenaModule module : modules) {
      module.onRoundEnd(arenaId, finishedRound, roundOutcome);
    }

    // Match-end cascade implies round-end; coordinator zeros both tiers on
    // MATCH scope. Pick the higher-tier reset; emitting both would overwrite
    // on the same arena-entity component slot.
    final boolean matchEnded =
        runMatchEndIfRequested(arenaEntityId, arenaId, set, modules, roundOutcome);
    final ScoreReset.Scope scope = matchEnded ? ScoreReset.Scope.MATCH : ScoreReset.Scope.ROUND;
    ed.setComponent(arenaEntityId, new ScoreReset(scope));

    final int nextRound = finishedRound + 1;
    ed.setComponent(arenaEntityId, new RoundNumber(nextRound));
    for (final ArenaModule module : modules) {
      module.onRoundStart(arenaId, nextRound);
    }
    ed.removeComponent(arenaEntityId, RoundEndPending.class);
  }

  private boolean runMatchEndIfRequested(
      final EntityId arenaEntityId,
      final ArenaId arenaId,
      final ArenaModuleSet set,
      final List<ArenaModule> modules,
      final RoundOutcome roundOutcome) {
    final Optional<MatchStructureModule> matchStructure = set.matchStructure();
    if (matchStructure.isEmpty() || !matchStructure.get().shouldMatchEnd(arenaId, roundOutcome)) {
      return false;
    }
    final MatchOutcome matchOutcome = new MatchOutcome(
        roundOutcome.winningFreq(), Map.of(), Map.of());
    for (final ArenaModule module : modules) {
      module.onMatchEnd(arenaId, matchOutcome);
    }
    final int nextMatch = currentMatch(arenaEntityId) + 1;
    ed.setComponent(arenaEntityId, new MatchNumber(nextMatch));
    for (final ArenaModule module : modules) {
      module.onMatchStart(arenaId);
    }
    return true;
  }

  private int currentRound(final EntityId arenaEntityId) {
    final RoundNumber current = ed.getComponent(arenaEntityId, RoundNumber.class);
    return current == null ? 1 : current.getValue();
  }

  private int currentMatch(final EntityId arenaEntityId) {
    final MatchNumber current = ed.getComponent(arenaEntityId, MatchNumber.class);
    return current == null ? 1 : current.getValue();
  }

  private static RoundOutcome buildRoundOutcome(final ArenaModuleSet set, final ArenaId arenaId) {
    final WinnerDeclaration winner = aggregateWinner(set, arenaId);
    return new RoundOutcome(winner.winningFreq(), winner.reason(), Map.of(), Map.of());
  }

  /** First-non-UNDECIDED-wins per ADR-0008 § Win condition two-role (v1 policy). */
  private static WinnerDeclaration aggregateWinner(
      final ArenaModuleSet set, final ArenaId arenaId) {
    for (final WinConditionModule wc : set.winConditions()) {
      final WinnerDeclaration vote = wc.declareWinner(arenaId);
      if (vote.winningFreq() != WinnerDeclaration.UNDECIDED.winningFreq()) {
        return vote;
      }
    }
    return WinnerDeclaration.UNDECIDED;
  }

  @Override
  public void start() {
    // intentionally empty
  }

  @Override
  public void stop() {
    // intentionally empty
  }
}
