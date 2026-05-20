// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.teamsetup;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.SimTime;
import infinity.Ship;
import infinity.es.ChangeTarget;
import infinity.es.Frequency;
import infinity.es.FrequencyChange;
import infinity.es.arena.ArenaId;
import infinity.es.ship.PlayerShip;
import infinity.es.ship.ShipType;
import infinity.es.team.TeamEntity;
import infinity.es.team.TeamMemberCount;
import infinity.modules.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Pins {@link TwoFixedTeamsTeamSetup}'s eager two-team creation + balanced join + member-count tracking. */
public final class TwoFixedTeamsTeamSetupTest {

  private DefaultEntityData ed;
  private ArenaId thisArena;
  private TwoFixedTeamsTeamSetup module;
  private EntitySet freqChanges;
  private EntitySet teams;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    thisArena = new ArenaId("trench", arenaEntity);
    module =
        new TwoFixedTeamsTeamSetup(new ModuleContext(thisArena, arenaEntity, ed, null, null));
    freqChanges = ed.getEntities(FrequencyChange.class, ChangeTarget.class);
    teams =
        ed.getEntities(TeamEntity.class, Frequency.class, ArenaId.class, TeamMemberCount.class);
  }

  @After
  public void tearDown() {
    module.onArenaUnload(thisArena);
    freqChanges.release();
    teams.release();
  }

  @Test
  public void onArenaLoad_eagerlyCreatesBothTeams_withZeroMembers() {
    module.onArenaLoad(thisArena);

    teams.applyChanges();
    assertEquals("two team entities created", 2, teams.size());
    final List<Integer> freqs = new ArrayList<>();
    for (final Entity team : teams) {
      freqs.add(team.get(Frequency.class).getFrequency());
      assertEquals("initial member count is zero",
          0, team.get(TeamMemberCount.class).count());
      assertEquals(thisArena.getArena(), team.get(ArenaId.class).getArena());
    }
    freqs.sort(Integer::compareTo);
    assertEquals(List.of(0, 1), freqs);
  }

  @Test
  public void firstPlayer_joinsFreq0() {
    module.onArenaLoad(thisArena);
    final EntityId ship = spawnPlayerShip(thisArena);

    module.tickTeamSetup(thisArena, simTimeAt(0L));

    freqChanges.applyChanges();
    assertEquals(1, freqChanges.size());
    final Entity intent = freqChanges.iterator().next();
    assertEquals(ship, intent.get(ChangeTarget.class).target());
    assertEquals("ties to freq 0",
        0, intent.get(FrequencyChange.class).newFrequency());

    teams.applyChanges();
    assertEquals("freq 0 gains the member, freq 1 stays at 0",
        List.of(1, 0), countsInFreqOrder());
  }

  @Test
  public void fourPlayers_balanceAcrossBothTeams() {
    module.onArenaLoad(thisArena);
    spawnPlayerShip(thisArena);
    spawnPlayerShip(thisArena);
    spawnPlayerShip(thisArena);
    spawnPlayerShip(thisArena);

    module.tickTeamSetup(thisArena, simTimeAt(0L));

    teams.applyChanges();
    assertEquals("2-2 split", List.of(2, 2), countsInFreqOrder());
  }

  @Test
  public void shipRemoved_decrementsOwningTeam() {
    module.onArenaLoad(thisArena);
    final EntityId a = spawnPlayerShip(thisArena);
    final EntityId b = spawnPlayerShip(thisArena);
    module.tickTeamSetup(thisArena, simTimeAt(0L));
    teams.applyChanges();
    assertEquals(List.of(1, 1), countsInFreqOrder());

    ed.removeEntity(a);
    module.tickTeamSetup(thisArena, simTimeAt(1L));

    teams.applyChanges();
    // a was on freq 0 (first joiner), so freq 0 drops to 0; freq 1 unchanged at 1.
    assertEquals(List.of(0, 1), countsInFreqOrder());
    // Suppress unused; b still alive, just here for symmetry.
    assertEquals(thisArena, ed.getComponent(b, ArenaId.class));
  }

  @Test
  public void onArenaUnload_despawnsBothTeams() {
    module.onArenaLoad(thisArena);
    teams.applyChanges();
    assertEquals(2, teams.size());

    module.onArenaUnload(thisArena);

    teams.applyChanges();
    assertEquals("both teams cleared on unload", 0, teams.size());
  }

  @Test
  public void nonPlayerShipIsIgnored() {
    module.onArenaLoad(thisArena);
    final EntityId bot = ed.createEntity();
    ed.setComponent(bot, thisArena);
    ed.setComponent(bot, new Frequency(0));
    ed.setComponent(bot, new ShipType(Ship.JAVELIN));
    // no PlayerShip marker

    module.tickTeamSetup(thisArena, simTimeAt(0L));

    freqChanges.applyChanges();
    assertEquals("bot generates no FrequencyChange", 0, freqChanges.size());
    teams.applyChanges();
    assertEquals("both team counts unchanged",
        List.of(0, 0), countsInFreqOrder());
  }

  /** Reads [memberCount(freq=0), memberCount(freq=1)] off the live team entities. */
  private List<Integer> countsInFreqOrder() {
    final int[] counts = new int[] {-1, -1};
    for (final Entity team : teams) {
      final int freq = team.get(Frequency.class).getFrequency();
      counts[freq] = team.get(TeamMemberCount.class).count();
    }
    return List.of(counts[0], counts[1]);
  }

  private EntityId spawnPlayerShip(final ArenaId arena) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, arena);
    ed.setComponent(ship, new Frequency(0));
    ed.setComponent(ship, new ShipType(Ship.WARBIRD));
    ed.setComponent(ship, new PlayerShip());
    return ship;
  }

  private static SimTime simTimeAt(final long nanos) {
    final SimTime t = new SimTime();
    t.update(nanos);
    return t;
  }
}
