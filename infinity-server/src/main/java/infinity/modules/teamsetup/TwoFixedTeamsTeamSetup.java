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
import infinity.es.ship.PlayerShip;
import infinity.es.ship.ShipType;
import infinity.es.team.TeamEntity;
import infinity.es.team.TeamMemberCount;
import infinity.modules.ModuleContext;
import infinity.modules.TeamSetupModule;
import java.util.HashMap;
import java.util.Map;

/**
 * Eager two-team setup (freq 0 + freq 1). Creates both {@link TeamEntity} instances at
 * {@code onArenaLoad}; team entities persist across the arena lifetime (members come and
 * go but the team slot does not). New player ships are balanced to the team with the
 * lower {@link TeamMemberCount} — tie breaks to freq 0. Ship-leave decrements the owning
 * team's count.
 *
 * <p>Canonical writer of {@link TeamEntity}, {@link TeamMemberCount}, and the team's
 * {@link Frequency}. Ship-side {@code Frequency} flows via {@link FrequencyChange} intent
 * drained by {@code FrequencySystem}.
 */
public final class TwoFixedTeamsTeamSetup implements TeamSetupModule {

  private static final int TEAM_COUNT = 2;

  private final EntityData ed;
  private final ArenaId arenaId;
  private final EntityId arenaEntityId;
  private EntitySet playerShips;
  private final Map<EntityId, Integer> shipToFreq = new HashMap<>();
  private final EntityId[] teamByFreq = new EntityId[TEAM_COUNT];
  private final int[] memberCounts = new int[TEAM_COUNT];

  public TwoFixedTeamsTeamSetup(final ModuleContext ctx) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.arenaEntityId = ctx.arenaEntity();
    this.playerShips =
        ed.getEntities(ArenaId.class, Frequency.class, ShipType.class, PlayerShip.class);
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    for (int freq = 0; freq < TEAM_COUNT; freq++) {
      final EntityId team = ed.createEntity();
      ed.setComponents(
          team,
          new TeamEntity(),
          new Frequency(freq),
          new ArenaId(arenaId.getArena(), arenaEntityId),
          new TeamMemberCount(0));
      teamByFreq[freq] = team;
      memberCounts[freq] = 0;
    }
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
    for (int freq = 0; freq < TEAM_COUNT; freq++) {
      if (teamByFreq[freq] != null) {
        ed.removeEntity(teamByFreq[freq]);
        teamByFreq[freq] = null;
      }
      memberCounts[freq] = 0;
    }
    shipToFreq.clear();
  }

  private void claimShip(final EntityId ship) {
    if (shipToFreq.containsKey(ship)) {
      return;
    }
    final int freq = balancedFreq();
    shipToFreq.put(ship, freq);
    memberCounts[freq]++;
    updateMemberCount(freq);
    emitFrequencyChange(ship, freq);
  }

  private void releaseShip(final EntityId ship) {
    final Integer freq = shipToFreq.remove(ship);
    if (freq == null) {
      return;
    }
    memberCounts[freq] = Math.max(0, memberCounts[freq] - 1);
    updateMemberCount(freq);
  }

  /** Pick the freq with fewer members; tie → freq 0. */
  private int balancedFreq() {
    return memberCounts[0] <= memberCounts[1] ? 0 : 1;
  }

  private void updateMemberCount(final int freq) {
    final EntityId team = teamByFreq[freq];
    if (team != null) {
      ed.setComponent(team, new TeamMemberCount(memberCounts[freq]));
    }
  }

  private void emitFrequencyChange(final EntityId ship, final int freq) {
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new FrequencyChange(freq));
  }
}
