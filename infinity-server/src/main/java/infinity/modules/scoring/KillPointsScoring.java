// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.scoring;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import infinity.config.KillPointsConfig;
import infinity.es.ChangeTarget;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerScoreChange;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ModuleContext;
import infinity.modules.ScoringModule;

/**
 * Awards {@code perKill} points to the killer on every {@link PlayerKilledEvent} in this arena.
 * Filters globally-published events by killer's {@link ArenaId} component. Skips unattributed
 * deaths (regen / environment) where {@code killer == null}.
 */
public final class KillPointsScoring implements ScoringModule {

  private final EntityData ed;
  private final ArenaId arenaId;
  private final int perKill;

  public KillPointsScoring(final ModuleContext ctx, final KillPointsConfig config) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.perKill = config.perKill();
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    EventBus.addListener(this, PlayerKilledEvent.playerKilled);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    EventBus.removeListener(this, PlayerKilledEvent.playerKilled);
  }

  /** EventBus reflective dispatch — name pattern is {@code on<EventTypeName>}. */
  public void onPlayerKilled(final PlayerKilledEvent event) {
    final EntityId killer = event.getKiller();
    if (killer == null) {
      return;
    }
    final ArenaId killerArena = ed.getComponent(killer, ArenaId.class);
    if (killerArena == null || !killerArena.equals(arenaId)) {
      return;
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, new ChangeTarget(killer, killer), new PlayerScoreChange(perKill));
  }
}
