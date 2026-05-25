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
import infinity.es.ship.BotShip;
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

/** Pins {@link FfaPrivateFreqsTeamSetup}'s claim/release semantics + team entity lifecycle. */
public final class FfaPrivateFreqsTeamSetupTest {

  private DefaultEntityData ed;
  private ArenaId thisArena;
  private ArenaId otherArena;
  private FfaPrivateFreqsTeamSetup module;
  private EntitySet freqChanges;
  private EntitySet teams;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    thisArena = new ArenaId("ffa", arenaEntity);
    otherArena = new ArenaId("trench", ed.createEntity());
    module = new FfaPrivateFreqsTeamSetup(
        new ModuleContext(thisArena, arenaEntity, ed, null, null));
    freqChanges = ed.getEntities(FrequencyChange.class, ChangeTarget.class);
    teams = ed.getEntities(TeamEntity.class, Frequency.class, ArenaId.class, TeamMemberCount.class);
  }

  @After
  public void tearDown() {
    module.onArenaUnload(thisArena);
    freqChanges.release();
    teams.release();
  }

  @Test
  public void firstPlayerShip_claimsFreq0_andCreatesTeamEntity() {
    final EntityId ship = spawnPlayerShip(thisArena, 0);

    module.tickTeamSetup(thisArena, simTimeAt(0L));

    freqChanges.applyChanges();
    teams.applyChanges();
    assertEquals("one FrequencyChange intent emitted", 1, freqChanges.size());
    final Entity intent = freqChanges.iterator().next();
    assertEquals(ship, intent.get(ChangeTarget.class).target());
    assertEquals(0, intent.get(FrequencyChange.class).newFrequency());

    assertEquals("one TeamEntity created", 1, teams.size());
    final Entity team = teams.iterator().next();
    assertEquals(0, team.get(Frequency.class).getFrequency());
    assertEquals(thisArena.getArena(), team.get(ArenaId.class).getArena());
    assertEquals(1, team.get(TeamMemberCount.class).count());
  }

  @Test
  public void twoPlayerShips_claimSequentialFreqs() {
    spawnPlayerShip(thisArena, 0);
    spawnPlayerShip(thisArena, 0);

    module.tickTeamSetup(thisArena, simTimeAt(0L));

    teams.applyChanges();
    final List<Integer> freqs = new ArrayList<>();
    for (final Entity team : teams) {
      freqs.add(team.get(Frequency.class).getFrequency());
    }
    freqs.sort(Integer::compareTo);
    assertEquals("two teams with sequential freqs", List.of(0, 1), freqs);
  }

  @Test
  public void botShip_claimsItsOwnFreq_likeAPlayer() {
    final EntityId bot = ed.createEntity();
    ed.setComponent(bot, thisArena);
    ed.setComponent(bot, new Frequency(1)); // factory seed; team setup reassigns
    ed.setComponent(bot, new ShipType(Ship.JAVELIN));
    ed.setComponent(bot, new BotShip()); // bot marker, not PlayerShip

    module.tickTeamSetup(thisArena, simTimeAt(0L));

    freqChanges.applyChanges();
    teams.applyChanges();
    assertEquals("bot is claimed → one FrequencyChange", 1, freqChanges.size());
    assertEquals("freq 0 (lowest free) assigned",
        0, freqChanges.iterator().next().get(FrequencyChange.class).newFrequency());
    assertEquals("bot gets its own TeamEntity", 1, teams.size());
  }

  @Test
  public void shipInOtherArena_ignored() {
    spawnPlayerShip(otherArena, 0);

    module.tickTeamSetup(thisArena, simTimeAt(0L));

    teams.applyChanges();
    assertEquals("foreign-arena ship ignored", 0, teams.size());
  }

  @Test
  public void shipRemoved_despawnsTeamEntity() {
    final EntityId ship = spawnPlayerShip(thisArena, 0);
    module.tickTeamSetup(thisArena, simTimeAt(0L));
    teams.applyChanges();
    assertEquals(1, teams.size());

    // Drop the ArenaId — ship "leaves the arena". The set filter no longer matches it.
    ed.removeEntity(ship);

    module.tickTeamSetup(thisArena, simTimeAt(1L));

    teams.applyChanges();
    assertEquals("team entity despawned on last member leaving", 0, teams.size());
  }

  @Test
  public void freedFreqIsReclaimedByNextShip() {
    // Claim 0, 1, 2.
    spawnPlayerShip(thisArena, 0);
    final EntityId s1 = spawnPlayerShip(thisArena, 0);
    spawnPlayerShip(thisArena, 0);
    module.tickTeamSetup(thisArena, simTimeAt(0L));

    // Free the middle freq.
    ed.removeEntity(s1);
    module.tickTeamSetup(thisArena, simTimeAt(1L));

    // A new ship should grab the freed freq (1), not 3.
    spawnPlayerShip(thisArena, 0);
    module.tickTeamSetup(thisArena, simTimeAt(2L));

    teams.applyChanges();
    final List<Integer> freqs = new ArrayList<>();
    for (final Entity t : teams) {
      freqs.add(t.get(Frequency.class).getFrequency());
    }
    freqs.sort(Integer::compareTo);
    assertEquals("reused middle freq", List.of(0, 1, 2), freqs);
  }

  @Test
  public void onArenaUnload_despawnsAllTeams() {
    spawnPlayerShip(thisArena, 0);
    spawnPlayerShip(thisArena, 0);
    module.tickTeamSetup(thisArena, simTimeAt(0L));
    teams.applyChanges();
    assertEquals(2, teams.size());

    module.onArenaUnload(thisArena);

    teams.applyChanges();
    assertEquals("all teams cleared on arena unload", 0, teams.size());
  }

  /** Creates a ship entity with the typical player-spawn component bundle. */
  private EntityId spawnPlayerShip(final ArenaId arena, final int seedFrequency) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, arena);
    ed.setComponent(ship, new Frequency(seedFrequency));
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
