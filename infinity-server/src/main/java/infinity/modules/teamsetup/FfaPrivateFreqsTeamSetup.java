// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.teamsetup;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.Frequency;
import infinity.es.FrequencyChange;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Player;
import infinity.es.ship.ShipType;
import infinity.es.team.TeamEntity;
import infinity.es.team.TeamMemberCount;
import infinity.modules.ModuleContext;
import infinity.modules.TeamSetupModule;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Each player ship in this arena gets its own freq. Per-tick: drains the {@code (ArenaId,
 * Frequency, ShipType, Player)} EntitySet — new player ships claim the lowest-free freq
 * (emits a {@link FrequencyChange} intent for {@code FrequencySystem} to drain), departing
 * ships release their freq. Per claimed freq, owns a {@code TeamEntity} entity carrying
 * {@code Frequency}, {@code ArenaId}, and {@link TeamMemberCount} — always 1 in FFA (the
 * type is forward-compat for multi-member team setups).
 *
 * <p>Initial-assignment policy: subsequent {@code Frequency} changes (e.g. via the
 * {@code =N} admin chat command) are NOT enforced. FFA private-freqs is initial-only.
 */
public final class FfaPrivateFreqsTeamSetup implements TeamSetupModule {

  private final EntityData ed;
  private final ArenaId arenaId;
  private final EntityId arenaEntityId;
  private EntitySet playerShips;
  private final Map<EntityId, Integer> shipToFreq = new HashMap<>();
  private final Map<Integer, EntityId> teamByFreq = new HashMap<>();
  private final NavigableSet<Integer> claimedFreqs = new TreeSet<>();

  public FfaPrivateFreqsTeamSetup(final ModuleContext ctx) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.arenaEntityId = ctx.arenaEntity();
    this.playerShips = ed.getEntities(ArenaId.class, Frequency.class, ShipType.class, Player.class);
  }

  @Override
  public void tickTeamSetup(final ArenaId arenaIdParam, final SimTime time) {
    if (playerShips == null || !playerShips.applyChanges()) {
      return;
    }
    for (final Entity removed : playerShips.getRemovedEntities()) {
      releaseShip(removed.getId());
    }
    for (final Entity added : playerShips.getAddedEntities()) {
      if (!arenaId.equals(added.get(ArenaId.class))) {
        continue;
      }
      claimShip(added.getId());
    }
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    if (playerShips != null) {
      playerShips.release();
      playerShips = null;
    }
    for (final EntityId teamId : teamByFreq.values()) {
      ed.removeEntity(teamId);
    }
    teamByFreq.clear();
    shipToFreq.clear();
    claimedFreqs.clear();
  }

  /** Pick the lowest-free freq, emit intent + create the team entity. */
  private void claimShip(final EntityId ship) {
    if (shipToFreq.containsKey(ship)) {
      return;
    }
    final int freq = nextFreeFreq();
    shipToFreq.put(ship, freq);
    claimedFreqs.add(freq);
    emitFrequencyChange(ship, freq);
    final EntityId team = ed.createEntity();
    ed.setComponents(
        team,
        new TeamEntity(),
        new Frequency(freq),
        new ArenaId(arenaId.getArena(), arenaEntityId),
        new TeamMemberCount(1));
    teamByFreq.put(freq, team);
  }

  /** Release a ship's freq; despawn the team entity when its member count hits zero. */
  private void releaseShip(final EntityId ship) {
    final Integer freq = shipToFreq.remove(ship);
    if (freq == null) {
      return;
    }
    final EntityId team = teamByFreq.get(freq);
    if (team == null) {
      claimedFreqs.remove(freq);
      return;
    }
    final TeamMemberCount current = ed.getComponent(team, TeamMemberCount.class);
    final int next = (current == null ? 1 : current.count()) - 1;
    if (next <= 0) {
      ed.removeEntity(team);
      teamByFreq.remove(freq);
      claimedFreqs.remove(freq);
      return;
    }
    ed.setComponent(team, new TeamMemberCount(next));
  }

  /** Find the lowest non-negative int not currently in {@link #claimedFreqs}. */
  private int nextFreeFreq() {
    int expected = 0;
    final Iterator<Integer> iter = claimedFreqs.iterator();
    while (iter.hasNext()) {
      final int claimed = iter.next();
      if (claimed != expected) {
        return expected;
      }
      expected++;
    }
    return expected;
  }

  private void emitFrequencyChange(final EntityId ship, final int freq) {
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new FrequencyChange(freq));
  }
}
