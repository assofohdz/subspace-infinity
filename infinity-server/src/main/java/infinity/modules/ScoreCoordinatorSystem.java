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
import infinity.es.score.PlayerRoundScore;
import infinity.es.score.PlayerScoreChange;
import infinity.es.score.ScoreReset;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical writer for {@link PlayerRoundScore} per ADR-0001 / ADR-0008. Drains
 * {@link PlayerScoreChange} transients emitted by scoring modules; sums deltas
 * per target in one drain pass. Missing prior score = treat as 0. One-shot:
 * change entities are destroyed after apply (no Decay-bound score buffs).
 */
public final class ScoreCoordinatorSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet changes;
  private EntitySet resets;
  private EntitySet scoredPlayers;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    changes = ed.getEntities(PlayerScoreChange.class, ChangeTarget.class);
    resets = ed.getEntities(ArenaId.class, ScoreReset.class);
    scoredPlayers = ed.getEntities(ArenaId.class, PlayerRoundScore.class);
  }

  @Override
  protected void terminate() {
    changes.release();
    changes = null;
    resets.release();
    resets = null;
    scoredPlayers.release();
    scoredPlayers = null;
  }

  @Override
  public void update(final SimTime time) {
    changes.applyChanges();
    resets.applyChanges();
    scoredPlayers.applyChanges();
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

  private void applyDelta(final EntityId target, final int delta) {
    if (delta == 0) {
      return;
    }
    final PlayerRoundScore current = ed.getComponent(target, PlayerRoundScore.class);
    final int base = current == null ? 0 : current.getValue();
    ed.setComponent(target, new PlayerRoundScore(base + delta));
  }

  /**
   * Drains {@link ScoreReset} markers on arena entities. ROUND scope zeros
   * {@link PlayerRoundScore} for every player in the arena. MATCH scope is
   * deferred to F2d when {@code PlayerMatchScore} lands.
   */
  private void drainScoreResets() {
    final Set<String> roundResetArenas = new HashSet<>();
    for (final Entity reset : resets.getAddedEntities()) {
      final ScoreReset marker = reset.get(ScoreReset.class);
      if (marker.scope() == ScoreReset.Scope.ROUND) {
        roundResetArenas.add(reset.get(ArenaId.class).getArena());
      }
      ed.removeComponent(reset.getId(), ScoreReset.class);
    }
    if (roundResetArenas.isEmpty()) {
      return;
    }
    for (final Entity player : scoredPlayers) {
      if (roundResetArenas.contains(player.get(ArenaId.class).getArena())) {
        ed.setComponent(player.getId(), new PlayerRoundScore(0));
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
