// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.SimTime;
import infinity.config.FlagHoldTimeConfig;
import infinity.es.ChangeTarget;
import infinity.es.Flag;
import infinity.es.FlagOwnership;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.score.TeamScoreChange;
import infinity.es.team.TeamEntity;
import infinity.es.team.TeamFlagHoldTicks;
import infinity.modules.ModuleContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins {@link FlagHoldTimeScoring} accumulator + per-tick emit + onRoundEnd reset.
 */
public final class FlagHoldTimeScoringTest {

  private static final String ARENA = "trench";

  private DefaultEntityData ed;
  private ArenaId arenaId;
  private FlagHoldTimeScoring module;
  private EntitySet teamScoreChanges;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    arenaId = new ArenaId(ARENA, ed.createEntity());
    teamScoreChanges = ed.getEntities(TeamScoreChange.class, ChangeTarget.class);
  }

  @After
  public void tearDown() {
    if (module != null) {
      module.onArenaUnload(arenaId);
    }
    teamScoreChanges.release();
  }

  @Test
  public void noOwnedFlags_emitsNothing() {
    module = newModule(5);
    spawnTeam(0);
    spawnTeam(1);

    module.tickContributions(arenaId, simTime(1.0));

    teamScoreChanges.applyChanges();
    assertEquals(0, teamScoreChanges.size());
  }

  @Test
  public void oneFlagHeldFor1sec_emits5TeamScoreChange_and5HoldTicks() {
    module = newModule(5);
    final EntityId team0 = spawnTeam(0);
    spawnFlag(0);

    module.tickContributions(arenaId, simTime(1.0));

    teamScoreChanges.applyChanges();
    assertEquals(1, teamScoreChanges.size());
    final Entity intent = teamScoreChanges.iterator().next();
    assertEquals(team0, intent.get(ChangeTarget.class).target());
    assertEquals(5, intent.get(TeamScoreChange.class).delta());

    final TeamFlagHoldTicks ticks = ed.getComponent(team0, TeamFlagHoldTicks.class);
    assertNotNull("TeamFlagHoldTicks written on team entity", ticks);
    assertEquals(5, ticks.ticks());
  }

  @Test
  public void twoFlagsHeldFor1sec_emits10_doubleRate() {
    module = newModule(5);
    final EntityId team0 = spawnTeam(0);
    spawnFlag(0);
    spawnFlag(0);

    module.tickContributions(arenaId, simTime(1.0));

    teamScoreChanges.applyChanges();
    assertEquals(1, teamScoreChanges.size());
    assertEquals(10, teamScoreChanges.iterator().next().get(TeamScoreChange.class).delta());
    assertEquals(10, ed.getComponent(team0, TeamFlagHoldTicks.class).ticks());
  }

  @Test
  public void subSecondTicks_accumulate_acrossMultipleCalls() {
    module = newModule(5);
    final EntityId team0 = spawnTeam(0);
    spawnFlag(0);

    // 0.1s → accumulator at 0.5, no emit
    module.tickContributions(arenaId, simTime(0.1));
    teamScoreChanges.applyChanges();
    assertEquals("no whole-number delta after 0.1s", 0, teamScoreChanges.size());
    assertNull(ed.getComponent(team0, TeamFlagHoldTicks.class));

    // Another 0.1s → accumulator at 1.0, emit delta=1
    module.tickContributions(arenaId, simTime(0.1));
    teamScoreChanges.applyChanges();
    assertEquals(1, teamScoreChanges.size());
    assertEquals(1, teamScoreChanges.iterator().next().get(TeamScoreChange.class).delta());
    assertEquals(1, ed.getComponent(team0, TeamFlagHoldTicks.class).ticks());
  }

  @Test
  public void onRoundEnd_zeroesTeamFlagHoldTicks_andAccumulator() {
    module = newModule(5);
    final EntityId team0 = spawnTeam(0);
    spawnFlag(0);
    module.tickContributions(arenaId, simTime(1.0));
    assertEquals(5, ed.getComponent(team0, TeamFlagHoldTicks.class).ticks());

    module.onRoundEnd(arenaId, 1, null);

    assertEquals("round-end zeroes TeamFlagHoldTicks",
        0, ed.getComponent(team0, TeamFlagHoldTicks.class).ticks());
  }

  @Test
  public void flagInDifferentArena_isIgnored() {
    module = newModule(5);
    spawnTeam(0);
    // Spawn a flag in a different arena
    final EntityId otherArenaEntity = ed.createEntity();
    final ArenaId other = new ArenaId("ffa", otherArenaEntity);
    final EntityId foreignFlag = ed.createEntity();
    ed.setComponent(foreignFlag, new Flag());
    ed.setComponent(foreignFlag, new FlagOwnership(0));
    ed.setComponent(foreignFlag, other);

    module.tickContributions(arenaId, simTime(1.0));

    teamScoreChanges.applyChanges();
    assertEquals("foreign-arena flag ignored", 0, teamScoreChanges.size());
  }

  private FlagHoldTimeScoring newModule(final int perSecondPerFlag) {
    return new FlagHoldTimeScoring(
        new ModuleContext(arenaId, arenaId.getOwner(), ed, null, null),
        new FlagHoldTimeConfig(perSecondPerFlag));
  }

  private EntityId spawnTeam(final int freq) {
    final EntityId team = ed.createEntity();
    ed.setComponent(team, new TeamEntity());
    ed.setComponent(team, new Frequency(freq));
    ed.setComponent(team, arenaId);
    return team;
  }

  private EntityId spawnFlag(final int ownerFreq) {
    final EntityId flag = ed.createEntity();
    ed.setComponent(flag, new Flag());
    ed.setComponent(flag, new FlagOwnership(ownerFreq));
    ed.setComponent(flag, arenaId);
    return flag;
  }

  /** SimTime with the requested tpf in seconds. Calls update() to compute fields. */
  private static SimTime simTime(final double tpfSeconds) {
    final SimTime t = new SimTime();
    final long deltaNanos = (long) (tpfSeconds * 1_000_000_000L);
    t.update(0);
    t.update(deltaNanos);
    return t;
  }
}
