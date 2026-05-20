// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.mechanic;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.event.EventBus;
import infinity.es.CrownHolder;
import infinity.es.arena.ArenaId;
import infinity.es.ship.ShipType;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.MechanicModule;
import infinity.modules.ModuleContext;
import infinity.modules.RoundOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KOTH crown-distribution mechanic. Canonical writer of {@link CrownHolder} on player ships.
 *
 * <ul>
 *   <li>{@code onRoundStart} — stamps {@code CrownHolder(1)} on every player ship in this arena.
 *   <li>{@code onPlayerKilled} (via {@link EventBus}) — on attributed kill, transfers all crowns
 *       from victim to killer (in-arena only); on unattributed kill, drops the crowns
 *       (CrownHolder removed from victim without transfer).
 *   <li>{@code onRoundEnd} — removes {@code CrownHolder} from every ship in this arena.
 * </ul>
 */
public final class Crowns implements MechanicModule {

  private static final Logger log = LoggerFactory.getLogger(Crowns.class);

  private final EntityData ed;
  private final ArenaId arenaId;
  private final EntitySet ships;
  private final EntitySet crownHolders;

  public Crowns(final ModuleContext ctx) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    // Filter on ShipType (not PlayerShip) so bots (PlayerShip-less, ShipType-bearing) also
    // get crowns at round-start. KOTH against fill-up-x-teams bots is the canonical smoke.
    this.ships = ed.getEntities(ArenaId.class, ShipType.class);
    this.crownHolders = ed.getEntities(ArenaId.class, CrownHolder.class);
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    EventBus.addListener(this, PlayerKilledEvent.playerKilled);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    EventBus.removeListener(this, PlayerKilledEvent.playerKilled);
    ships.release();
    crownHolders.release();
  }

  @Override
  public void onRoundStart(final ArenaId arenaIdParam, final int roundNumber) {
    ships.applyChanges();
    int distributed = 0;
    for (final Entity ship : ships) {
      if (!arenaId.equals(ship.get(ArenaId.class))) {
        continue;
      }
      ed.setComponent(ship.getId(), new CrownHolder(1));
      distributed++;
    }
    if (log.isInfoEnabled()) {
      log.info("Crowns: distributed {} crowns at round {} for arena {}",
          distributed, roundNumber, arenaId.getArena());
    }
  }

  @Override
  public void onRoundEnd(
      final ArenaId arenaIdParam, final int roundNumber, final RoundOutcome outcome) {
    crownHolders.applyChanges();
    int cleared = 0;
    for (final Entity holder : crownHolders) {
      if (!arenaId.equals(holder.get(ArenaId.class))) {
        continue;
      }
      ed.removeComponent(holder.getId(), CrownHolder.class);
      cleared++;
    }
    if (log.isInfoEnabled()) {
      log.info("Crowns: cleared {} crowns at round-end {} for arena {}",
          cleared, roundNumber, arenaId.getArena());
    }
  }

  /** EventBus reflective dispatch — name pattern is {@code on<EventTypeName>}. */
  public void onPlayerKilled(final PlayerKilledEvent event) {
    final EntityId victim = event.getVictim();
    if (victim == null || !inThisArena(victim)) {
      return;
    }
    final int transferred = takeVictimCrowns(victim);
    if (transferred <= 0) {
      return;
    }
    creditKillerIfEligible(event.getKiller(), victim, transferred);
  }

  private boolean inThisArena(final EntityId entity) {
    final ArenaId arena = ed.getComponent(entity, ArenaId.class);
    return arena != null && arena.equals(arenaId);
  }

  /** Removes victim's CrownHolder if non-empty; returns the crowns the kill released (0 if none). */
  private int takeVictimCrowns(final EntityId victim) {
    final CrownHolder victimCrowns = ed.getComponent(victim, CrownHolder.class);
    if (victimCrowns == null || victimCrowns.crowns() <= 0) {
      return 0;
    }
    ed.removeComponent(victim, CrownHolder.class);
    return victimCrowns.crowns();
  }

  /** Adds {@code transferred} to killer's CrownHolder if the killer is attributed + in-arena + not self. */
  private void creditKillerIfEligible(
      final EntityId killer, final EntityId victim, final int transferred) {
    if (killer == null || killer.equals(victim) || !inThisArena(killer)) {
      if (log.isInfoEnabled()) {
        log.info("Crowns: {} crown(s) dropped from victim {} (unattributed/self/foreign killer)",
            transferred, victim);
      }
      return;
    }
    final CrownHolder killerCrowns = ed.getComponent(killer, CrownHolder.class);
    final int prev = killerCrowns == null ? 0 : killerCrowns.crowns();
    final int total = prev + transferred;
    ed.setComponent(killer, new CrownHolder(total));
    if (log.isInfoEnabled()) {
      log.info("Crowns: {} -> {} ({} + {} = {} crowns) in arena {}",
          victim, killer, prev, transferred, total, arenaId.getArena());
    }
  }
}
