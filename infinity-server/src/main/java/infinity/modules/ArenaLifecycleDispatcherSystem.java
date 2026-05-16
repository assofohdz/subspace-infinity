// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.es.arena.RoundEndPending;
import infinity.es.arena.RoundNumber;
import infinity.es.score.ScoreReset;
import infinity.modules.ArenaModuleSystem.LoadedArena;
import infinity.sim.ArenaModule;
import infinity.systems.BaseInfinitySystem;
import java.util.List;
import java.util.Map;


/**
 * Phase-4 dispatcher per ADR-0008. Watches the arena entity for {@link RoundEndPending}
 * markers (set by a {@code roundStructure} or {@code winCondition} terminator); on each:
 * fires {@code onRoundEnd} on every loaded module in registration order, emits a
 * {@link ScoreReset} for the round tier, fires {@code onRoundStart} for the next round,
 * increments {@link RoundNumber}, and clears the marker.
 *
 * <p>F2b: {@code RoundOutcome} carries empty {@code teamScores} (no aggregation yet);
 * match-end + winner aggregation land in F2c / F2d.
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
    final LoadedArena entry = moduleSystem.loadedFor(arenaEntity.getId());
    if (entry == null) {
      ed.removeComponent(arenaEntity.getId(), RoundEndPending.class);
      return;
    }
    final ArenaId arenaId = entry.arenaId();
    final RoundNumber current = ed.getComponent(arenaEntity.getId(), RoundNumber.class);
    final int finishedRound = current == null ? 1 : current.getValue();
    final WinnerDeclaration winner = aggregateWinner(entry.set(), arenaId);
    final RoundOutcome outcome =
        new RoundOutcome(winner.winningFreq(), winner.reason(), Map.of(), Map.of());

    final List<ArenaModule> modules = entry.set().allModules();
    for (final ArenaModule module : modules) {
      module.onRoundEnd(arenaId, finishedRound, outcome);
    }

    ed.setComponent(arenaEntity.getId(), new ScoreReset(ScoreReset.Scope.ROUND));

    final int nextRound = finishedRound + 1;
    ed.setComponent(arenaEntity.getId(), new RoundNumber(nextRound));
    for (final ArenaModule module : modules) {
      module.onRoundStart(arenaId, nextRound);
    }
    ed.removeComponent(arenaEntity.getId(), RoundEndPending.class);
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
