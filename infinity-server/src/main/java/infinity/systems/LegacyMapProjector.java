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
 * Projects a Subspace .lvl tile grid onto Moss world cells + ECS entities.
 * Cell-backed tiles (visible Subspace tiles 1..190 minus entity-backed ids)
 * become a single world cell at Y=1 with block type
 * {@code arenaTileBase + tileId - 1}. Entity-backed tiles (turf flags,
 * asteroids, doors, wormholes) spawn the matching {@link MapFactory} entity
 * and leave the cell empty. Canonical writer of arena-tile world cells (with
 * {@link WallLightDecorator}).
 */
public final class LegacyMapProjector {

  private static final Logger log = LoggerFactory.getLogger(LegacyMapProjector.class);

  private final EntityData ed;
  private final PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private final World world;
  private final EngineConfigProvider engineConfigProvider;

  /** Resolves the current {@link EngineConfig} at projection time (so hot-reload swaps are picked up). */
  @FunctionalInterface
  public interface EngineConfigProvider {
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

  /** @return every world-cell location written by this pass (so unload can clear them). */
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
        stats.incrementTotalNonZero();
        stats.getIdHistogram().merge(Short.toUnsignedInt(s), 1, Integer::sum);
        final Vec3d location = new Vec3d(xpos, 1, zpos).add(arenaOffset);
        coordinates.add(location);
        if (!spawnTileEntity(s, location, createdTime, stats)) {
          writeTileCell(s, location, arenaTileBase, stats, coordinates);
        }
      }
    }

    MapSystemLogic.logMapBuildSummary(log, map, arenaOffset, stats);
    WallLightDecorator.decorate(tiles, arenaOffset, world, coordinates);
    return coordinates;
  }

  /** Returns {@code true} if an entity was spawned (caller skips the cell-write fallback). */
  private boolean spawnTileEntity(
      final short s,
      final Vec3d location,
      final long createdTime,
      final MapSystemLogic.MapBuildStats stats) {
    final EngineConfig engineCfg = engineConfigProvider.get();
    if (s == MapTypes.VIE_TURF_FLAG) {
      MapFactory.createTurfStationaryFlag(
          ed,
          new infinity.sim.specs.TurfStationaryFlagArgs(
              EntityId.NULL_ID, physicsSpace, createdTime, location, engineCfg.flagRadius()));
      stats.incrementTurfFlags();
      return true;
    }
    // VIE_ASTEROID_SMALL / VIE_ASTEROID_MEDIUM / VIE_ASTEROID_END are now written as
    // mworld cells (block types ANIMATED_ASTEROID_*_BLOCK_TYPE) — see writeTileCell.
    // Falls through.
    if (s >= MapTypes.VIE_V_DOOR_START && s <= MapTypes.VIE_H_DOOR_END) {
      MapFactory.createDoor(
          ed,
          new infinity.sim.specs.DoorArgs(null, physicsSpace, createdTime, 5000, location));
      stats.incrementDoors();
      return true;
    }
    if (s == MapTypes.VIE_WORMHOLE) {
      MapFactory.createWormhole(
          ed,
          new infinity.sim.specs.WormholeArgs(
              null,
              physicsSpace,
              createdTime,
              location,
              5000,
              GravityWell.PULL,
              new Vec3d(0, 0, 0),
              1));
      stats.incrementWormholes();
      return true;
    }
    return false;
  }

  /**
   * Tiles 1..{@link InfinityConstants#MAX_VISIBLE_TILE} → arena's visible-tile range;
   * {@link MapTypes#VIE_ASTEROID_SMALL} → {@link InfinityConstants#ANIMATED_ASTEROID_SMALL_BLOCK_TYPE}
   * (1×1 animated mworld block; collision == static block);
   * {@link MapTypes#VIE_ASTEROID_MEDIUM} → {@link InfinityConstants#ANIMATED_ASTEROID_MEDIUM_BLOCK_TYPE}
   * with 3 spillover {@code INVISIBLE_BLOCK_TYPE} cells at +X/+Z/+X+Z (2×2 visual + 2×2 collision,
   * matching canonical OVER2SIZE=2.0 / over2Radius=1.0). Canonical Subspace maps leave 2-tile
   * clearance around medium asteroids; map authors who put wall tiles next to a medium asteroid
   * will see those cells overwritten with invisible-collision blocks, which matches the rendering
   * intent of canonical clients;
   * {@link MapTypes#VIE_ASTEROID_END} → {@link InfinityConstants#ANIMATED_ASTEROID_END_BLOCK_TYPE}
   * (4×4 decorative animated block, no collider) written at Y=2 (overlay layer) so the large
   * visual quad doesn't Z-fight with overlapped wall tiles at Y=1;
   * others → {@link InfinityConstants#INVISIBLE_BLOCK_TYPE}.
   */
  private void writeTileCell(
      final short s,
      final Vec3d location,
      final int arenaTileBase,
      final MapSystemLogic.MapBuildStats stats,
      final Set<Vec3d> coordinates) {
    final int tileId = Short.toUnsignedInt(s);

    if (tileId == MapTypes.VIE_ASTEROID_END) {
      writeOverlayDecoration(location, stats, coordinates);
      return;
    }

    final int blockType = resolveBlockType(tileId, arenaTileBase);
    final int result = world.setWorldCell(location, blockType);
    if (result == -1) {
      stats.incrementCellsFailedLeaf();
    } else if (blockType == InfinityConstants.INVISIBLE_BLOCK_TYPE) {
      stats.incrementCellsInvisible();
    } else {
      stats.incrementCellsVisible();
      if (blockType == InfinityConstants.ANIMATED_ASTEROID_SMALL_BLOCK_TYPE) {
        stats.incrementAsteroidsSmall();
      } else if (blockType == InfinityConstants.ANIMATED_ASTEROID_MEDIUM_BLOCK_TYPE) {
        stats.incrementAsteroidsMedium();
        writeMediumAsteroidSpillover(location, stats, coordinates);
      }
    }
    if (stats.getFirstWritten() == null) {
      stats.setFirstWritten(location);
    }
    stats.setLastWritten(location);
  }

  private static int resolveBlockType(final int tileId, final int arenaTileBase) {
    if (tileId == MapTypes.VIE_ASTEROID_SMALL) {
      return InfinityConstants.ANIMATED_ASTEROID_SMALL_BLOCK_TYPE;
    }
    if (tileId == MapTypes.VIE_ASTEROID_MEDIUM) {
      return InfinityConstants.ANIMATED_ASTEROID_MEDIUM_BLOCK_TYPE;
    }
    if (tileId >= 1 && tileId <= InfinityConstants.MAX_VISIBLE_TILE) {
      return arenaTileBase + tileId - 1;
    }
    return InfinityConstants.INVISIBLE_BLOCK_TYPE;
  }

  /**
   * Asteroid-end / large decorative asteroid: writes the animated cell on the Y=2 overlay
   * layer (one above the Y=1 collision plane in {@code location}) so the 4×4 visual quad
   * doesn't Z-fight with overlapped wall tiles. No collider, no spillover.
   */
  private void writeOverlayDecoration(
      final Vec3d location,
      final MapSystemLogic.MapBuildStats stats,
      final Set<Vec3d> coordinates) {
    final Vec3d overlay = new Vec3d(location.x, location.y + 1, location.z);
    final int r = world.setWorldCell(overlay, InfinityConstants.ANIMATED_ASTEROID_END_BLOCK_TYPE);
    if (r == -1) {
      stats.incrementCellsFailedLeaf();
    } else {
      stats.incrementCellsVisible();
      stats.incrementOver5();
      coordinates.add(overlay);
    }
    if (stats.getFirstWritten() == null) {
      stats.setFirstWritten(overlay);
    }
    stats.setLastWritten(overlay);
  }

  /**
   * Fills the 3 collision-only cells (+X, +Z, +X+Z) around a medium asteroid so the 2×2 visual
   * has matching 2×2 collision coverage. Cells are tracked in {@code coordinates} so map unload
   * clears them.
   */
  private void writeMediumAsteroidSpillover(
      final Vec3d origin,
      final MapSystemLogic.MapBuildStats stats,
      final Set<Vec3d> coordinates) {
    final Vec3d[] spillover = {
        new Vec3d(origin.x + 1, origin.y, origin.z),
        new Vec3d(origin.x,     origin.y, origin.z + 1),
        new Vec3d(origin.x + 1, origin.y, origin.z + 1)
    };
    for (final Vec3d pos : spillover) {
      final int r = world.setWorldCell(pos, InfinityConstants.INVISIBLE_BLOCK_TYPE);
      if (r == -1) {
        stats.incrementCellsFailedLeaf();
      } else {
        stats.incrementCellsInvisible();
        coordinates.add(pos);
      }
    }
  }
}
