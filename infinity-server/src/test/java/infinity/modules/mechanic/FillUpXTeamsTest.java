// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.mechanic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
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
import infinity.modules.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Pins {@link FillUpXTeams}'s freq-occupancy decision. Uses a subclass test seam to
 * record spawn requests without invoking the physics-bound {@code AIEntities.createMobShip}. */
public final class FillUpXTeamsTest {

  private static final String ARENA_NAME = "ffa";
  private static final String MAP_FILE = "test.lvl";

  @Test
  public void emptyArena_spawnsBotForEveryFreq() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    ed.setComponent(arenaEntity, arenaId);
    ed.setComponent(arenaEntity, new ArenaMap(
        new Vec3d(0, 0, 0), new Vec3d(1024, 4, 1024), MAP_FILE, 0));

    final RecordingFillUpXTeams m = new RecordingFillUpXTeams(
        new ModuleContext(arenaId, arenaEntity, ed, null, null),
        new FillUpXTeamsConfig(3));
    m.onArenaLoad(arenaId);

    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("one spawn per freq 0..2", List.of(0, 1, 2), m.spawnedFreqs);
  }

  @Test
  public void botsRoster_drivesSpawnCount_overridingTeams() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    ed.setComponent(arenaEntity, arenaId);
    ed.setComponent(arenaEntity, new ArenaMap(
        new Vec3d(0, 0, 0), new Vec3d(1024, 4, 1024), MAP_FILE, 0));

    // Roster of 3 (warbird×2 + shark×1) overrides teams=2 → 3 freqs filled.
    final BotsConfig bots =
        new BotsConfig(List.of(new BotShipConfig(Ship.WARBIRD, 2), new BotShipConfig(Ship.SHARK, 1)));
    final RecordingFillUpXTeams m = new RecordingFillUpXTeams(
        new ModuleContext(arenaId, arenaEntity, ed, null, null, null, bots),
        new FillUpXTeamsConfig(2));
    m.onArenaLoad(arenaId);

    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("roster size (3) drives spawn count, not teams (2)", List.of(0, 1, 2), m.spawnedFreqs);
  }

  @Test
  public void existingPlayerOnFreq0_spawnsOnlyMissingFreqs() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    ed.setComponent(arenaEntity, arenaId);
    ed.setComponent(arenaEntity, new ArenaMap(
        new Vec3d(0, 0, 0), new Vec3d(1024, 4, 1024), MAP_FILE, 0));

    // Pre-existing player on freq 0.
    final EntityId player = ed.createEntity();
    ed.setComponent(player, arenaId);
    ed.setComponent(player, new Frequency(0));

    final RecordingFillUpXTeams m = new RecordingFillUpXTeams(
        new ModuleContext(arenaId, arenaEntity, ed, null, null),
        new FillUpXTeamsConfig(2));
    m.onArenaLoad(arenaId);

    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("freq 0 already filled; only freq 1 bot spawned",
        List.of(1), m.spawnedFreqs);
  }

  @Test
  public void deadShip_doesNotCount_respawnsForThatFreq() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    ed.setComponent(arenaEntity, arenaId);
    ed.setComponent(arenaEntity, new ArenaMap(
        new Vec3d(0, 0, 0), new Vec3d(1024, 4, 1024), MAP_FILE, 0));

    // Dead ship on freq 0 — should not count.
    final EntityId ghost = ed.createEntity();
    ed.setComponent(ghost, arenaId);
    ed.setComponent(ghost, new Frequency(0));
    ed.setComponent(ghost, new Dead(0L));

    final RecordingFillUpXTeams m = new RecordingFillUpXTeams(
        new ModuleContext(arenaId, arenaEntity, ed, null, null),
        new FillUpXTeamsConfig(1));
    m.onArenaLoad(arenaId);

    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("dead ship doesn't satisfy freq 0; fresh bot spawned",
        List.of(0), m.spawnedFreqs);
  }

  @Test
  public void foreignArenaShip_doesNotCount() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ffaEntity = ed.createEntity();
    final EntityId trenchEntity = ed.createEntity();
    final ArenaId ffa = new ArenaId(ARENA_NAME, ffaEntity);
    final ArenaId trench = new ArenaId("trench", trenchEntity);
    ed.setComponent(ffaEntity, ffa);
    ed.setComponent(ffaEntity, new ArenaMap(
        new Vec3d(0, 0, 0), new Vec3d(1024, 4, 1024), MAP_FILE, 0));
    // Player in trench on freq 0 — should NOT satisfy ffa's freq 0.
    final EntityId trenchPlayer = ed.createEntity();
    ed.setComponent(trenchPlayer, trench);
    ed.setComponent(trenchPlayer, new Frequency(0));

    final RecordingFillUpXTeams m = new RecordingFillUpXTeams(
        new ModuleContext(ffa, ffaEntity, ed, null, null),
        new FillUpXTeamsConfig(1));
    m.onArenaLoad(ffa);

    m.tickMechanic(ffa, simTimeAt(0L));

    assertTrue("trench's freq 0 ignored", m.spawnedFreqs.contains(0));
  }

  @Test
  public void noArenaMapYet_doesNotSpawn() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    ed.setComponent(arenaEntity, arenaId);
    // No ArenaMap yet — arena not fully loaded.

    final RecordingFillUpXTeams m = new RecordingFillUpXTeams(
        new ModuleContext(arenaId, arenaEntity, ed, null, null),
        new FillUpXTeamsConfig(2));
    m.onArenaLoad(arenaId);

    m.tickMechanic(arenaId, simTimeAt(0L));

    assertTrue("no spawns until ArenaMap is present", m.spawnedFreqs.isEmpty());
  }

  private static SimTime simTimeAt(final long simNanos) {
    final SimTime t = new SimTime();
    t.setCurrentTime(simNanos);
    t.update(0L);
    return t;
  }

  /** Records spawn requests instead of invoking the physics-bound factory. */
  private static final class RecordingFillUpXTeams extends FillUpXTeams {
    final List<Integer> spawnedFreqs = new ArrayList<>();

    RecordingFillUpXTeams(final ModuleContext ctx, final FillUpXTeamsConfig cfg) {
      super(ctx, cfg);
    }

    @Override
    protected EntityId spawnBot(final long createdTimeNanos, final int freq) {
      spawnedFreqs.add(freq);
      return null;
    }
  }
}
