// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.World;
import infinity.InfinityConstants;
import infinity.config.EngineConfig;
import infinity.es.GravityWell;
import infinity.map.LevelFile;
import infinity.map.MapTypes;
import infinity.sim.MapFactory;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Projects a legacy Subspace .lvl tile grid onto Moss world cells + ECS entities. Pulled out
 * of {@code MapSystem} so the host system can stay focused on map lifecycle (load/unload/swap)
 * while the per-tile dispatch + cell-write strategy lives here as an independently testable
 * unit.
 *
 * <p>Cell-backed tiles (visible Subspace tiles 1..190 minus entity-backed ids) become a
 * single world cell at Y=1 with block type {@code arenaTileBase + tileId - 1}. Entity-backed
 * tiles (turf flags, asteroids, doors, wormholes) spawn the matching {@link MapFactory}
 * entity and leave the cell empty. Every touched location (including entity positions) is
 * returned so the unload pass can zero the slot cleanly.
 *
 * <p>Per <a href="../../../../../.claude/rules/replacement-as-mutation.md">RaM</a>, the
 * projector + the wall-light decorator together are the single canonical writer of
 * {@code World} cells for arena-tile block types and {@link InfinityConstants#LIGHT_EMITTER_BLOCK_TYPE}
 * emitters; {@code MapSystem} owns the lifecycle around them but does not write cells
 * directly.
 *
 * <p>Constructed with the per-projector dependencies ({@link EntityData},
 * {@link PhysicsSpace}, {@link World}, optional {@link EngineConfig} provider). The
 * {@link #project} call takes the per-map params (file, offset, tileBase, createdTime).
 */
public final class LegacyMapProjector {

  private static final Logger log = LoggerFactory.getLogger(LegacyMapProjector.class);

  private final EntityData ed;
  private final PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private final World world;
  private final EngineConfigProvider engineConfigProvider;

  /**
   * Resolves the current {@link EngineConfig} at projection time. Pulled to an interface
   * (rather than passing {@code EngineConfig} directly) so the projector picks up
   * Groovy-reload swaps when {@code MapSystem} reuses the same projector instance across
   * map loads.
   */
  @FunctionalInterface
  public interface EngineConfigProvider {
    /** @return the current engine config; never {@code null} */
    EngineConfig get();
  }

  public LegacyMapProjector(
      final EntityData ed,
      final PhysicsSpace<EntityId, MBlockShape> physicsSpace,
      final World world,
      final EngineConfigProvider engineConfigProvider) {
    this.ed = ed;
    this.physicsSpace = physicsSpace;
    this.world = world;
    this.engineConfigProvider =
        engineConfigProvider == null ? () -> EngineConfig.DEFAULTS : engineConfigProvider;
  }

  /**
   * Writes a legacy .lvl map into the world at the given offset. Cell-backed tiles become
   * a single world cell at Y=1; entity-backed tiles (flags, asteroids, doors, wormholes)
   * spawn their entity and leave the cell empty. Every touched location (including entity
   * positions) is returned so unload can zero the slot cleanly.
   *
   * <p>Also runs the wall-run light decorator over the same tile grid so emitter cells
   * are placed in the same projection pass.
   *
   * @param map           parsed .lvl
   * @param arenaOffset   world-space offset to add to each cell location
   * @param arenaTileBase per-arena tile-block-type base index (see
   *                      {@link InfinityConstants#arenaTileBase(int)})
   * @param createdTime   sim timestamp captured on the caller thread (entity-backed tiles
   *                      stamp this on their {@code Decay}/spawn-time markers)
   * @return every world-cell location written by this pass (so unload can clear)
   */
  public Set<Vec3d> project(
      final LevelFile map,
      final Vec3d arenaOffset,
      final int arenaTileBase,
      final long createdTime) {
    final Set<Vec3d> coordinates = new HashSet<>();
    final short[][] tiles = map.getMap();
    final MapSystemLogic.MapBuildStats stats = new MapSystemLogic.MapBuildStats();

    final int extentX = tiles.length - 1;
    for (int xpos = 0; xpos < tiles.length; xpos++) {
      final int extentZ = tiles[xpos].length - 1;
      for (int zpos = 0; zpos < tiles[xpos].length; zpos++) {
        final short s = tiles[extentX - xpos][extentZ - zpos];
        if (s == 0) {
          continue;
        }
        stats.totalNonZero++;
        stats.idHistogram.merge(Short.toUnsignedInt(s), 1, Integer::sum);
        final Vec3d location = new Vec3d(xpos, 1, zpos).add(arenaOffset);
        coordinates.add(location);
        if (!spawnTileEntity(s, location, createdTime, stats)) {
          writeTileCell(s, location, arenaTileBase, stats);
        }
      }
    }

    MapSystemLogic.logMapBuildSummary(log, map, arenaOffset, stats);
    WallLightDecorator.decorate(tiles, arenaOffset, world, coordinates);
    return coordinates;
  }

  /**
   * Per-tile dispatch for entity-backed map cells (turf flags, asteroids, doors, wormholes).
   * Spawns the matching {@link MapFactory} entity for recognised tile ids and bumps the
   * matching counter on {@code stats}; returns {@code true} if an entity was spawned (caller
   * skips the cell-write step), {@code false} if the tile id needs the cell-write fallback.
   */
  private boolean spawnTileEntity(
      final short s,
      final Vec3d location,
      final long createdTime,
      final MapSystemLogic.MapBuildStats stats) {
    final EngineConfig engineCfg = engineConfigProvider.get();
    if (s == MapTypes.vieTurfFlag) {
      MapFactory.createTurfStationaryFlag(
          ed, EntityId.NULL_ID, physicsSpace, createdTime, location, engineCfg.flagRadius());
      stats.turfFlags++;
      return true;
    }
    if (s == MapTypes.vieAsteroidSmall) {
      MapFactory.createAsteroidSmall(
          ed, null, physicsSpace, createdTime, location, 0, engineCfg.over1Radius());
      stats.asteroidsSmall++;
      return true;
    }
    if (s == MapTypes.vieAsteroidMedium) {
      MapFactory.createAsteroidMedium(
          ed, null, physicsSpace, createdTime, location, 0, engineCfg.over2Radius());
      stats.asteroidsMedium++;
      return true;
    }
    if (s == MapTypes.vieAsteroidEnd) {
      MapFactory.createOver5(
          ed, null, physicsSpace, createdTime, location, engineCfg.over5Radius());
      stats.over5++;
      return true;
    }
    if (s >= MapTypes.vieVDoorStart && s <= MapTypes.vieHDoorEnd) {
      MapFactory.createDoor(ed, null, physicsSpace, createdTime, 5000, location);
      stats.doors++;
      return true;
    }
    if (s == MapTypes.vieWormhole) {
      MapFactory.createWormhole(
          ed, null, physicsSpace, createdTime, location,
          5000, GravityWell.PULL, new Vec3d(0, 0, 0), 1);
      stats.wormholes++;
      return true;
    }
    return false;
  }

  /**
   * Cell-write path for non-entity tile ids: 1..{@link InfinityConstants#MAX_VISIBLE_TILE}
   * get the arena's visible-tile range; everything else (incl. invalid ids) is written as
   * {@link InfinityConstants#INVISIBLE_BLOCK_TYPE}. Updates {@code stats} counters.
   */
  private void writeTileCell(
      final short s,
      final Vec3d location,
      final int arenaTileBase,
      final MapSystemLogic.MapBuildStats stats) {
    final int tileId = Short.toUnsignedInt(s);
    final int blockType = (tileId >= 1 && tileId <= InfinityConstants.MAX_VISIBLE_TILE)
        ? arenaTileBase + tileId - 1
        : InfinityConstants.INVISIBLE_BLOCK_TYPE;
    final int result = world.setWorldCell(location, blockType);
    if (result == -1) {
      stats.cellsFailedLeaf++;
    } else if (blockType == InfinityConstants.INVISIBLE_BLOCK_TYPE) {
      stats.cellsInvisible++;
    } else {
      stats.cellsVisible++;
    }
    if (stats.firstWritten == null) {
      stats.firstWritten = location;
    }
    stats.lastWritten = location;
  }
}
