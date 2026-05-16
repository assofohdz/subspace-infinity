// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.mechanic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.config.FillUpXTeamsConfig;
import infinity.es.Dead;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
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
  public void pendingNerf_clampsEnergyAndStats_onceProjected() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    ed.setComponent(arenaEntity, arenaId);
    ed.setComponent(arenaEntity, new ArenaMap(
        new Vec3d(0, 0, 0), new Vec3d(1024, 4, 1024), MAP_FILE, 0));

    final SpawnRecordingFillUpXTeams m = new SpawnRecordingFillUpXTeams(
        new ModuleContext(arenaId, arenaEntity, ed, null, null),
        new FillUpXTeamsConfig(1));
    m.onArenaLoad(arenaId);

    // Tick 1: bot spawn recorded; nerf pending.
    m.tickMechanic(arenaId, simTimeAt(0L));
    final EntityId bot = m.spawned.get(0);
    assertTrue("bot tracked as pending", m.isPendingNerf(bot));

    // Simulate ShipSpawnSystem projecting full Javelin stats.
    ed.setComponent(bot, new Energy(1500));
    ed.setComponent(bot, new EnergyStats(1500, 1500, 0, 150.0, 150.0, 0.0));

    // Tick 2: nerf drain sees EnergyStats present → clamps.
    m.tickMechanic(arenaId, simTimeAt(0L));

    assertEquals("Energy clamped to BOT_HP", 100,
        ed.getComponent(bot, Energy.class).getEnergy());
    final EnergyStats clamped = ed.getComponent(bot, EnergyStats.class);
    assertEquals("max clamped", 100, clamped.max());
    assertEquals("recharge zeroed", 0.0, clamped.rechargePerSecond(), 0.001);
    assertTrue("removed from pending after clamp", !m.isPendingNerf(bot));
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

  /** Records real spawned EntityIds + queues them for the nerf path via super.spawnBot's
   * tracking. Exposes pendingNerf membership for assertions. */
  private static final class SpawnRecordingFillUpXTeams extends FillUpXTeams {
    final List<EntityId> spawned = new ArrayList<>();
    private final DefaultEntityData ed;

    SpawnRecordingFillUpXTeams(final ModuleContext ctx, final FillUpXTeamsConfig cfg) {
      super(ctx, cfg);
      this.ed = (DefaultEntityData) ctx.ed();
    }

    @Override
    protected EntityId spawnBot(final long createdTimeNanos, final int freq) {
      // Bypass the AIEntities factory (no PhysicsSpace in tests) but produce a
      // real EntityId so the nerf drain has a target.
      final EntityId bot = ed.createEntity();
      ed.setComponent(bot, new Frequency(freq));
      spawned.add(bot);
      pendingNerfForTest().add(bot);
      return bot;
    }

    private java.util.Set<EntityId> pendingNerfForTest() {
      try {
        final var f = FillUpXTeams.class.getDeclaredField("pendingNerf");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        final java.util.Set<EntityId> set = (java.util.Set<EntityId>) f.get(this);
        return set;
      } catch (final ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    boolean isPendingNerf(final EntityId id) {
      return pendingNerfForTest().contains(id);
    }
  }
}
