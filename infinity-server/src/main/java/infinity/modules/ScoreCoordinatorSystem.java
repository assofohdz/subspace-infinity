// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.score.PlayerRoundScore;
import infinity.es.score.PlayerScoreChange;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical writer for {@link PlayerRoundScore} per ADR-0001 / ADR-0008. Drains
 * {@link PlayerScoreChange} transients emitted by scoring modules; sums deltas
 * per target in one drain pass. Missing prior score = treat as 0. One-shot:
 * change entities are destroyed after apply (no Decay-bound score buffs).
 */
public final class ScoreCoordinatorSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet changes;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    changes = ed.getEntities(PlayerScoreChange.class, ChangeTarget.class);
  }

  @Override
  protected void terminate() {
    changes.release();
    changes = null;
  }

  @Override
  public void update(final SimTime time) {
    changes.applyChanges();
    drainPlayerScoreChanges();
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

  @Override
  public void start() {
    // intentionally empty
  }

  @Override
  public void stop() {
    // intentionally empty
  }
}
