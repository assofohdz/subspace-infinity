// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.scoring;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import infinity.config.CrownKillBonusConfig;
import infinity.es.ChangeTarget;
import infinity.es.CrownHolder;
import infinity.es.arena.ArenaId;
import infinity.es.score.PlayerScoreChange;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ModuleContext;
import infinity.modules.ScoringModule;

/**
 * Additive scoring layer for KOTH: when the killer holds at least one {@link CrownHolder},
 * emits an extra {@link PlayerScoreChange} of
 * {@code perCrownKill * killer.crowns}. Composes alongside {@code KillPointsScoring} —
 * both write the same intent stream, {@code ScoreCoordinatorSystem} sums per-target.
 *
 * <p>Filters globally-published events by killer's {@link ArenaId} component.
 */
public final class CrownKillBonus implements ScoringModule {

  private final EntityData ed;
  private final ArenaId arenaId;
  private final int perCrownKill;

  public CrownKillBonus(final ModuleContext ctx, final CrownKillBonusConfig config) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.perCrownKill = config.effectivePerCrownKill();
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
    final CrownHolder crowns = ed.getComponent(killer, CrownHolder.class);
    if (crowns == null || crowns.crowns() <= 0) {
      return;
    }
    final int bonus = perCrownKill * crowns.crowns();
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, new ChangeTarget(killer, killer), new PlayerScoreChange(bonus));
  }
}
