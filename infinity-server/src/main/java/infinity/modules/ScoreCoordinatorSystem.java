// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerMatchScore;
import infinity.es.score.PlayerRoundScore;
import infinity.es.score.PlayerScoreChange;
import infinity.es.score.PlayerTotalScore;
import infinity.es.score.ScoreReset;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical writer for the three player-tier score components
 * ({@link PlayerRoundScore}, {@link PlayerMatchScore}, {@link PlayerTotalScore})
 * per ADR-0001 / ADR-0008. Each drained {@link PlayerScoreChange} adds its delta
 * to ALL three tiers in one pass — the three tiers diverge only when
 * {@link ScoreReset} markers zero a tier on round-end (ROUND) or match-end
 * (MATCH). {@link PlayerTotalScore} accumulates across the arena session.
 *
 * <p>Missing prior tier value = treat as 0. One-shot: change entities are
 * destroyed after apply (no Decay-bound score buffs in F2*).
 */
public final class ScoreCoordinatorSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet changes;
  private EntitySet resets;
  private EntitySet roundScored;
  private EntitySet matchScored;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    changes = ed.getEntities(PlayerScoreChange.class, ChangeTarget.class);
    resets = ed.getEntities(ArenaId.class, ScoreReset.class);
    roundScored = ed.getEntities(ArenaId.class, PlayerRoundScore.class);
    matchScored = ed.getEntities(ArenaId.class, PlayerMatchScore.class);
  }

  @Override
  protected void terminate() {
    changes.release();
    changes = null;
    resets.release();
    resets = null;
    roundScored.release();
    roundScored = null;
    matchScored.release();
    matchScored = null;
  }

  @Override
  public void update(final SimTime time) {
    changes.applyChanges();
    resets.applyChanges();
    roundScored.applyChanges();
    matchScored.applyChanges();
    drainPlayerScoreChanges();
    drainScoreResets();
  }

  private void drainPlayerScoreChanges() {
    final Map<EntityId, Integer> deltaByTarget = new HashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final int delta = added.get(PlayerScoreChange.class).delta();
      deltaByTarget.merge(ct.target(), delta, Integer::sum);
      oneShotHolders.add(added.getId());
    }
    for (final Map.Entry<EntityId, Integer> e : deltaByTarget.entrySet()) {
      applyDelta(e.getKey(), e.getValue());
    }
    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }
  }

  /** Adds {@code delta} to all three player-tier components in one pass. */
  private void applyDelta(final EntityId target, final int delta) {
    if (delta == 0) {
      return;
    }
    final PlayerRoundScore round = ed.getComponent(target, PlayerRoundScore.class);
    final PlayerMatchScore match = ed.getComponent(target, PlayerMatchScore.class);
    final PlayerTotalScore total = ed.getComponent(target, PlayerTotalScore.class);
    ed.setComponent(target, new PlayerRoundScore((round == null ? 0 : round.getValue()) + delta));
    ed.setComponent(target, new PlayerMatchScore((match == null ? 0 : match.getValue()) + delta));
    ed.setComponent(target, new PlayerTotalScore((total == null ? 0 : total.getValue()) + delta));
  }

  /**
   * Drains {@link ScoreReset} markers on arena entities. ROUND zeros
   * {@link PlayerRoundScore}; MATCH zeros {@link PlayerMatchScore} AND
   * {@link PlayerRoundScore} (match-end implies round-end semantically — the
   * dispatcher only emits the higher-tier scope to avoid same-tick component
   * overwrites). {@link PlayerTotalScore} never resets.
   */
  private void drainScoreResets() {
    final Map<ScoreReset.Scope, Set<String>> arenasByScope = new EnumMap<>(ScoreReset.Scope.class);
    for (final Entity reset : resets.getAddedEntities()) {
      final ScoreReset marker = reset.get(ScoreReset.class);
      arenasByScope
          .computeIfAbsent(marker.scope(), k -> new HashSet<>())
          .add(reset.get(ArenaId.class).getArena());
      ed.removeComponent(reset.getId(), ScoreReset.class);
    }
    final Set<String> matchArenas = arenasByScope.getOrDefault(ScoreReset.Scope.MATCH, Set.of());
    // ROUND tier zeroed for both ROUND and MATCH scopes.
    final Set<String> roundArenas = new HashSet<>(
        arenasByScope.getOrDefault(ScoreReset.Scope.ROUND, Set.of()));
    roundArenas.addAll(matchArenas);
    if (!roundArenas.isEmpty()) {
      for (final Entity p : roundScored) {
        if (roundArenas.contains(p.get(ArenaId.class).getArena())) {
          ed.setComponent(p.getId(), new PlayerRoundScore(0));
        }
      }
    }
    if (!matchArenas.isEmpty()) {
      for (final Entity p : matchScored) {
        if (matchArenas.contains(p.get(ArenaId.class).getArena())) {
          ed.setComponent(p.getId(), new PlayerMatchScore(0));
        }
      }
    }
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
