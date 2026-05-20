// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.team.TeamEntity;
import infinity.es.team.TeamFlagHoldTicks;
import infinity.modules.ModuleContext;
import infinity.modules.WinnerDeclaration;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins {@link MostFlagOccupancyWinCondition}'s {@code declareWinner}: highest
 * {@link TeamFlagHoldTicks} wins; UNDECIDED when no team has any.
 */
public final class MostFlagOccupancyWinConditionTest {

  private DefaultEntityData ed;
  private ArenaId arenaId;
  private MostFlagOccupancyWinCondition module;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    arenaId = new ArenaId("trench", ed.createEntity());
    module = new MostFlagOccupancyWinCondition(
        new ModuleContext(arenaId, arenaId.getOwner(), ed, null, null));
  }

  @Test
  public void noTeamHasHeldFlags_returnsUndecided() {
    spawnTeam(0);
    spawnTeam(1);

    assertEquals(WinnerDeclaration.UNDECIDED, module.declareWinner(arenaId));
  }

  @Test
  public void singleTeamWithHoldTicks_wins() {
    final EntityId team0 = spawnTeam(0);
    spawnTeam(1);
    ed.setComponent(team0, new TeamFlagHoldTicks(120));

    final WinnerDeclaration decl = module.declareWinner(arenaId);

    assertEquals(0, decl.winningFreq());
    assertTrue(decl.reason().contains("120"));
  }

  @Test
  public void higherHoldTicks_wins_overLower() {
    final EntityId team0 = spawnTeam(0);
    final EntityId team1 = spawnTeam(1);
    ed.setComponent(team0, new TeamFlagHoldTicks(40));
    ed.setComponent(team1, new TeamFlagHoldTicks(80));

    assertEquals(1, module.declareWinner(arenaId).winningFreq());
  }

  @Test
  public void otherArenaTeams_areIgnored() {
    final ArenaId otherArena = new ArenaId("ffa", ed.createEntity());
    final EntityId team0 = spawnTeam(0);
    ed.setComponent(team0, new TeamFlagHoldTicks(50));
    // Team in other arena with much higher hold time
    final EntityId foreign = ed.createEntity();
    ed.setComponent(foreign, new TeamEntity());
    ed.setComponent(foreign, new Frequency(1));
    ed.setComponent(foreign, otherArena);
    ed.setComponent(foreign, new TeamFlagHoldTicks(9999));

    assertEquals("foreign arena team ignored",
        0, module.declareWinner(arenaId).winningFreq());
  }

  private EntityId spawnTeam(final int freq) {
    final EntityId team = ed.createEntity();
    ed.setComponent(team, new TeamEntity());
    ed.setComponent(team, new Frequency(freq));
    ed.setComponent(team, arenaId);
    return team;
  }
}
