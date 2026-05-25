// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.mechanic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.Ship;
import infinity.config.BotShipConfig;
import infinity.config.BotsConfig;
import infinity.config.FillUpXTeamsConfig;
import infinity.es.Dead;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.BotShip;
import infinity.modules.ArenaModuleSet;
import infinity.modules.ArenaModuleSetLookup;
import infinity.modules.ModuleContext;
import infinity.modules.TeamSetupModule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.Test;

/**
 * Pins {@link FillUpXTeams}'s count-based fill decision. Bounded team-setups
 * ({@code fixedTeamCount × capacity}) fill seats and cull surplus bots; unbounded (FFA)
 * keeps a flat bot count. Uses a subclass test seam to record spawn requests without
 * invoking the physics-bound {@code AIEntities.createMobShip}.
 */
public final class FillUpXTeamsTest {

  private static final String ARENA = "test";
  private static final String MAP = "test.lvl";

  // ---------------------------------------------------------------- bounded (e.g. two-fixed-teams)

  @Test
  public void boundedTwoTeams_emptyArena_fillsTeamsTimesCapacity() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);

    // 2 teams × capacity 2 = 4 seats, no ships present → 4 bots.
    final RecordingFillUpXTeams m = bounded(ed, arena, arenaId, 2, capacityCfg(2), BotsConfig.DEFAULTS);
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("fixedTeamCount(2) × capacity(2) seats filled", 4, m.spawnCount);
  }

  @Test
  public void boundedTwoTeams_humansFillSeats_botsTakeRemainder() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);
    seedShip(ed, arenaId, /*bot=*/ false, /*dead=*/ false); // one human takes a seat

    final RecordingFillUpXTeams m = bounded(ed, arena, arenaId, 2, capacityCfg(2), BotsConfig.DEFAULTS);
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("4 seats − 1 human = 3 bots", 3, m.spawnCount);
  }

  @Test
  public void boundedTwoTeams_surplusBots_culledWhenOverCapacity() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);
    // 4 live bots already present; capacity 1 × 2 teams = 2 seats → 2 surplus.
    for (int i = 0; i < 4; i++) {
      seedShip(ed, arenaId, /*bot=*/ true, /*dead=*/ false);
    }

    final RecordingFillUpXTeams m = bounded(ed, arena, arenaId, 2, capacityCfg(1), BotsConfig.DEFAULTS);
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("over capacity → no spawns", 0, m.spawnCount);
    assertEquals("2 surplus bots culled down to the 2 seats", 2, countLiveBots(ed, arenaId));
  }

  // ----------------------------------------------------------------------------- unbounded (FFA)

  @Test
  public void ffa_rosterSizeDrivesBotCount() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);
    final BotsConfig bots =
        new BotsConfig(List.of(new BotShipConfig(Ship.WARBIRD, 2), new BotShipConfig(Ship.SHARK, 1)));

    // modules == null → unbounded (FFA); roster of 3 drives the count.
    final RecordingFillUpXTeams m =
        new RecordingFillUpXTeams(ctx(arenaId, arena, ed, null, bots), new FillUpXTeamsConfig(8));
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("roster size (3) drives count, not teams (8)", 3, m.spawnCount);
  }

  @Test
  public void ffa_noRoster_fallsBackToTeamsConfig() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);

    final RecordingFillUpXTeams m =
        new RecordingFillUpXTeams(ctx(arenaId, arena, ed, null, BotsConfig.DEFAULTS), new FillUpXTeamsConfig(4));
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("no roster → teams (4) is the bot count", 4, m.spawnCount);
  }

  @Test
  public void ffa_deadBotsExcluded_triggerRespawn() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);
    seedShip(ed, arenaId, /*bot=*/ true, /*dead=*/ true); // a corpse must not count as live

    final RecordingFillUpXTeams m =
        new RecordingFillUpXTeams(ctx(arenaId, arena, ed, null, BotsConfig.DEFAULTS), new FillUpXTeamsConfig(2));
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("dead bot excluded → full target re-spawned", 2, m.spawnCount);
  }

  @Test
  public void ffa_countPerPlayer_scalesWithPlayers() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);
    for (int i = 0; i < 3; i++) {
      seedShip(ed, arenaId, /*bot=*/ false, /*dead=*/ false); // 3 humans
    }

    // teams=2 base + countPerPlayer 1 × 3 players = 5.
    final RecordingFillUpXTeams m =
        new RecordingFillUpXTeams(
            ctx(arenaId, arena, ed, null, BotsConfig.DEFAULTS), new FillUpXTeamsConfig(2, 1));
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("base(2) + perPlayer(1)×players(3) = 5", 5, m.spawnCount);
  }

  // ------------------------------------------------------------------ churn regression (#orphan)

  @Test
  public void boundedTwoTeams_stuckBotLosesArenaId_notRespawned() {
    // Regression: a stuck bot stops generating arena-sensor contacts, so
    // ArenaMembershipSystem's exit-grace sweep strips its ArenaId and it drops out of the
    // ArenaId-gated arenaShips set — while still alive. Counting bots via spawnedBots (not
    // arenaShips alone) is what stops FillUpXTeams from re-spawning a replacement each tick.
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);

    final EntityCreatingFillUpXTeams m = boundedCreating(ed, arena, arenaId, 2, capacityCfg(2));
    m.onArenaLoad(arenaId);

    // Tick 1: empty arena, 2 teams × capacity 2 = 4 seats → 4 real bots, tracked.
    m.tickMechanic(arenaId, simTimeAt(0L));
    assertEquals("4 seats filled on first tick", 4, m.spawnCount);
    assertEquals("4 live bots registered", 4, countLiveBots(ed, arenaId));

    // Every bot goes stuck and loses its ArenaId (membership exit-grace sweep).
    for (final EntityId bot : m.spawned) {
      ed.removeComponent(bot, ArenaId.class);
    }

    // Tick 2: bots are invisible to arenaShips but still alive in spawnedBots → no respawn.
    m.tickMechanic(arenaId, simTimeAt(1L));
    assertEquals("de-ArenaId'd live bots must not trigger a respawn storm", 4, m.spawnCount);
  }

  // ----------------------------------------------------------------------------------- guards

  @Test
  public void noArenaMapYet_doesNotSpawn() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA, arena);
    ed.setComponent(arena, arenaId); // no ArenaMap — arena not fully loaded

    final RecordingFillUpXTeams m =
        new RecordingFillUpXTeams(ctx(arenaId, arena, ed, null, BotsConfig.DEFAULTS), new FillUpXTeamsConfig(2));
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("no spawns until ArenaMap present", 0, m.spawnCount);
  }

  @Test
  public void foreignArenaShip_doesNotCount() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arena = newArena(ed);
    final ArenaId arenaId = new ArenaId(ARENA, arena);
    final EntityId otherArena = ed.createEntity();
    final ArenaId other = new ArenaId("other", otherArena);
    seedShip(ed, other, /*bot=*/ false, /*dead=*/ false); // ship in another arena

    final RecordingFillUpXTeams m =
        new RecordingFillUpXTeams(ctx(arenaId, arena, ed, null, BotsConfig.DEFAULTS), new FillUpXTeamsConfig(2));
    m.onArenaLoad(arenaId);
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertTrue("foreign ship ignored → full target spawned", m.spawnCount == 2);
  }

  // ------------------------------------------------------------------------------------- helpers

  private static FillUpXTeamsConfig capacityCfg(final int capacity) {
    return new FillUpXTeamsConfig(0, 0, capacity);
  }

  /** Bounded fill-up: stub a {@link TeamSetupModule} reporting {@code teamCount} fixed teams. */
  private static RecordingFillUpXTeams bounded(
      final DefaultEntityData ed,
      final EntityId arena,
      final ArenaId arenaId,
      final int teamCount,
      final FillUpXTeamsConfig cfg,
      final BotsConfig bots) {
    final TeamSetupModule teamSetup =
        new TeamSetupModule() {
          @Override
          public OptionalInt fixedTeamCount() {
            return OptionalInt.of(teamCount);
          }
        };
    final ArenaModuleSet set =
        new ArenaModuleSet(
            Optional.of(teamSetup),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            Map.of());
    final ArenaModuleSetLookup lookup = id -> set;
    return new RecordingFillUpXTeams(ctx(arenaId, arena, ed, lookup, bots), cfg);
  }

  /** Bounded fill-up backed by a recorder that creates real bot entities (populates spawnedBots). */
  private static EntityCreatingFillUpXTeams boundedCreating(
      final DefaultEntityData ed,
      final EntityId arena,
      final ArenaId arenaId,
      final int teamCount,
      final FillUpXTeamsConfig cfg) {
    final TeamSetupModule teamSetup =
        new TeamSetupModule() {
          @Override
          public OptionalInt fixedTeamCount() {
            return OptionalInt.of(teamCount);
          }
        };
    final ArenaModuleSet set =
        new ArenaModuleSet(
            Optional.of(teamSetup),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            Map.of());
    final ArenaModuleSetLookup lookup = id -> set;
    return new EntityCreatingFillUpXTeams(
        ctx(arenaId, arena, ed, lookup, BotsConfig.DEFAULTS), cfg, ed, arenaId);
  }

  private static ModuleContext ctx(
      final ArenaId arenaId,
      final EntityId arena,
      final DefaultEntityData ed,
      final ArenaModuleSetLookup lookup,
      final BotsConfig bots) {
    return new ModuleContext(arenaId, arena, ed, null, null, lookup, bots);
  }

  private static EntityId newArena(final DefaultEntityData ed) {
    final EntityId arena = ed.createEntity();
    ed.setComponent(arena, new ArenaId(ARENA, arena));
    ed.setComponent(arena, new ArenaMap(new Vec3d(0, 0, 0), new Vec3d(1024, 4, 1024), MAP, 0));
    return arena;
  }

  private static void seedShip(
      final DefaultEntityData ed, final ArenaId arenaId, final boolean bot, final boolean dead) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, arenaId);
    ed.setComponent(ship, new Frequency(1));
    if (bot) {
      ed.setComponent(ship, new BotShip());
    }
    if (dead) {
      ed.setComponent(ship, new Dead(0L));
    }
  }

  private static int countLiveBots(final DefaultEntityData ed, final ArenaId arenaId) {
    final EntitySet bots = ed.getEntities(BotShip.class, ArenaId.class);
    bots.applyChanges();
    int count = 0;
    for (final com.simsilica.es.Entity e : bots) {
      if (arenaId.equals(e.get(ArenaId.class)) && ed.getComponent(e.getId(), Dead.class) == null) {
        count++;
      }
    }
    bots.release();
    return count;
  }

  private static SimTime simTimeAt(final long simNanos) {
    final SimTime t = new SimTime();
    t.setCurrentTime(simNanos);
    t.update(0L);
    return t;
  }

  /** Records spawn requests instead of invoking the physics-bound factory. */
  private static final class RecordingFillUpXTeams extends FillUpXTeams {
    int spawnCount;

    RecordingFillUpXTeams(final ModuleContext ctx, final FillUpXTeamsConfig cfg) {
      super(ctx, cfg);
    }

    @Override
    protected EntityId createBotEntity(final long createdTimeNanos) {
      spawnCount++;
      return null;
    }
  }

  /**
   * Creates real (minimal) bot entities so {@code spawnedBots} is populated — used to
   * reproduce the de-{@code ArenaId} churn that the counting fix prevents.
   */
  private static final class EntityCreatingFillUpXTeams extends FillUpXTeams {
    int spawnCount;
    final List<EntityId> spawned = new java.util.ArrayList<>();
    private final DefaultEntityData ed;
    private final ArenaId arenaId;

    EntityCreatingFillUpXTeams(
        final ModuleContext ctx,
        final FillUpXTeamsConfig cfg,
        final DefaultEntityData ed,
        final ArenaId arenaId) {
      super(ctx, cfg);
      this.ed = ed;
      this.arenaId = arenaId;
    }

    @Override
    protected EntityId createBotEntity(final long createdTimeNanos) {
      spawnCount++;
      final EntityId bot = ed.createEntity();
      ed.setComponent(bot, arenaId);
      ed.setComponent(bot, new Frequency(1));
      ed.setComponent(bot, new BotShip());
      spawned.add(bot);
      return bot;
    }
  }
}
