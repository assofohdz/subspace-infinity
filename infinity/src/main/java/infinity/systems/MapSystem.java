// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.TileId;
import com.simsilica.mworld.World;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.InfinityConstants;
import infinity.es.GravityWell;
import infinity.es.TileTypes;
import infinity.map.LevelFile;
import infinity.map.MapTypes;
import infinity.map.LevelLoader;
import infinity.server.AssetLoaderService;
import infinity.sim.CoreViewConstants;
import infinity.sim.GameEntities;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State to keep track of loaded maps, load them, unload them The map names are unique identifiers
 * of each map.
 *
 * @author Asser
 */
public class MapSystem extends AbstractGameSystem {

  public static final byte CREATE = 0x0;
  public static final byte READ = 0x1;
  public static final byte UPDATE = 0x2;
  public static final byte DELETE = 0x3;
  public static final int MAP_SIZE = InfinityConstants.TILE_SIZE;
  public static final float NOISE4J_CORRIDOR = 0f;
  public static final float NOISE4J_FLOOR = 0.5f;
  public static final float NOISE4J_WALL = 1f;

  /** Maximum tile ID for visible tiles (1-190 in Subspace). */
  public static final int MAX_VISIBLE_TILE = 190;

  /**
   * Compute the block-type base index for a given arena slot — see
   * {@link InfinityConstants#TILE_TYPE_BASE} / {@link InfinityConstants#TILE_COUNT} for the
   * canonical layout shared with the server collider array and the client BlockGeometryIndex.
   */
  public static int arenaTileBase(final int arenaIndex) {
    return InfinityConstants.TILE_TYPE_BASE + arenaIndex * InfinityConstants.TILE_COUNT;
  }

  /**
   * Block type index for invisible physics blocks. These blocks have collision but no visible
   * geometry. Must match BlockGeometryIndex.INVISIBLE_BLOCK_TYPE_INDEX.
   */
  public static final int INVISIBLE_BLOCK_TYPE = 11;

  /**
   * Block type index for light-emitter cells. Must match
   * BlockGeometryIndex.LIGHT_EMITTER_BLOCK_TYPE_INDEX. Non-solid, transparent, invisible;
   * its BlockType emission is picked up by LightUtils.recalculateLighting flood fill.
   */
  public static final int LIGHT_EMITTER_BLOCK_TYPE = 12;

  private static final int HALF = MAP_SIZE / 2;
  static Logger log = LoggerFactory.getLogger(MapSystem.class);
  private final String mapDirectory = "Maps";
  // Map that holds all block coordinates for a given map:
  private final HashMap<String, HashSet<Vec3d>> activeMaps = new HashMap<>();
  // Map that holds the offset coordinates of each map:
  private final LinkedHashMap<String, Vec3d> mapCoordinates = new LinkedHashMap<>();
  private Vec3d currentMapLoc = new Vec3d(-1, 0, -1);
  private EntityData ed;
  private MPhysSystem<MBlockShape> physics;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private SimTime time;
  // private EntitySet tileTypes;
  private AssetLoaderService assetLoader;
  private World world;
  private Direction direction = Direction.S;

  public MapSystem() {}

  protected MPhysSystem<MBlockShape> getPhysicsSystem() {
    final MPhysSystem<?> s = getSystem(MPhysSystem.class);
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> result = (MPhysSystem<MBlockShape>) s;
    return result;
  }

  public void setCell(Vec3d pos, int type) {
    world.setWorldCell(pos, type);
  }

  // int[][] multD = new int[5][];
  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    if (ed == null) {
      throw new RuntimeException(getClass().getName() + " system requires an EntityData object.");
    }
    physics = getPhysicsSystem();
    if (physics == null) {
      throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
    }
    world = super.getManager().get(World.class);
    // world = getSystem(DefaultLeafWorld.class);
    if (world == null) {
      throw new RuntimeException(getClass().getName() + " system requires the World system.");
    }
    this.assetLoader = getSystem(AssetLoaderService.class);

    physicsSpace = physics.getPhysicsSpace();
    assetLoader.registerLoader(LevelLoader.class, "lvl", "lvz");

    // Create entities, so the tile types will be in the string index (we use the
    // tiletypes as filters)
    final EntityId e = ed.createEntity();
    final short s = 0;
    ed.setComponent(e, TileTypes.legacy("empty", s, ed));

    final EntityId e2 = ed.createEntity();
    final short s2 = 0;
    ed.setComponent(e2, TileTypes.wangblob("empty", s2, ed));
  }

  /**
   * Finds the center of an arena. Uses MAP_SIZE to calculate center
   *
   * @param currentxCoord the x-coordinate
   * @param currentzCoord the y-coordinate
   * @return the Vec3d center coordinate
   */
  public Vec3d getCenterOfArena(final double currentxCoord, final double currentzCoord) {
    final double xArenaCoord = Math.floor(currentxCoord / MAP_SIZE);
    final double zArenaCoord = Math.floor(currentzCoord / MAP_SIZE);

    final double centerOfArenaX = currentxCoord < 0 ? xArenaCoord - HALF : xArenaCoord + HALF;
    final double centerOfArenaZ = currentzCoord < 0 ? zArenaCoord - HALF : zArenaCoord + HALF;

    return new Vec3d(centerOfArenaX, 1, centerOfArenaZ);
  }

  private Vec3d calculateNextOffset() {
    Direction testDirection = direction.next();
    Vec3d testMapLoc = testDirection.advance(currentMapLoc);
    // First time we will land here:
    if (!mapCoordinates.containsValue(currentMapLoc)) {
      log.info("Currentmap location is:" + currentMapLoc + ", current direction is:" + direction);
      return currentMapLoc;
    } else if (!mapCoordinates.containsValue(testMapLoc)) {

      // Test if we should go new direction
      currentMapLoc = testMapLoc;
      direction = testDirection;
      log.info("Currentmap location is:" + currentMapLoc + ", current direction is:" + direction);
      return currentMapLoc;
    }

    // If we have to continue straight ahead in our direction:
    currentMapLoc = direction.advance(currentMapLoc);
    log.info("Currentmap location is:" + currentMapLoc + ", current direction is:" + direction);
    return currentMapLoc;
  }

  /**
   * Loads a map, auto-positioning it via the spiral placement algorithm.
   *
   * @param mapName    the lvl-map to load
   * @param arenaIndex zero-based arena slot — determines the block-type range the map's tiles
   *                   occupy ({@code arenaTileBase(arenaIndex)..+189}), and therefore which
   *                   tileset the client will render them with.
   * @return true if loaded
   */
  public boolean loadMap(final String mapName, final int arenaIndex) {
    if (!(mapName.endsWith(".lvl") || mapName.endsWith(".lvz"))) {
      return false;
    }
    Vec3d offset = calculateNextOffset();
    TileId tile = TileId.fromCell((int) offset.x, 0, (int) offset.z);
    return loadMap(mapName, tile, arenaIndex);
  }

  /**
   * Loads a map at an explicit grid location. Each TileId is a 1024x1024 slot on Moss's TILE_GRID;
   * adjacent tiles share a 2-cell gutter formed by each map's own border ring.
   *
   * @param mapName    the lvl-map to load
   * @param tile       the Moss TileId specifying where to place the map
   * @param arenaIndex zero-based arena slot (see {@link #loadMap(String, int)})
   * @return true if loaded, false if the filename is invalid or the tile is occupied
   */
  public boolean loadMap(final String mapName, final TileId tile, final int arenaIndex) {
    log.info("Loading map: " + mapName + " at " + tile + " (arenaIndex=" + arenaIndex + ")");
    if (!(mapName.endsWith(".lvl") || mapName.endsWith(".lvz"))) {
      return false;
    }
    Vec3i cell = tile.getCell(null);
    Vec3d offset = new Vec3d(cell.x, cell.y, cell.z);
    if (mapCoordinates.containsValue(offset)) {
      log.warn("Tile " + tile + " already occupied; skipping " + mapName);
      return false;
    }
    Vec3i corner = tile.getWorld(null);
    Vec3d worldOffset = new Vec3d(corner.x, corner.y, corner.z);

    String fileName = mapDirectory + "/" + mapName;
    LevelFile res = (LevelFile) assetLoader.loadAsset(fileName);

    final int tileBase = arenaTileBase(arenaIndex);

    // Snapshot the sim timestamp synchronously on the caller thread. The async block
    // population must NOT read this.time from a worker thread — at startup the first
    // arena auto-loads before MapSystem.update has ever run, so this.time is null and
    // any entity-create branch (door / wormhole / asteroid / turfflag) NPEs out.
    // Trench's map happens to have no entity-trigger tiles so it dodges the NPE; deva
    // has wormholes / doors and falls in.
    final long createdTime = time != null ? time.getTime() : 0L;
    // Loading a map takes ~3 seconds depending on density — do it asynchronously.
    // Attach an exceptionally handler so any throw inside createBlocksFromLegacyMap
    // is logged instead of silently swallowed by the future (which would otherwise
    // leave the arena visible-cube up but no blocks rendered, with zero log evidence).
    CompletableFuture<HashSet<Vec3d>> completableFuture =
        CompletableFuture.supplyAsync(
            () -> this.createBlocksFromLegacyMap(res, worldOffset, tileBase, createdTime));
    completableFuture
        .thenAccept(s -> activeMaps.put(mapName, s))
        .exceptionally(ex -> {
          log.error("Async block population failed for map " + mapName, ex);
          return null;
        });

    res.setMapName(mapName);
    mapCoordinates.put(mapName, offset);
    log.info("Queued map: " + mapName + " at grid " + offset + " (world " + worldOffset + ")");
    return true;
  }

  /**
   * Returns the maximum world-space bounds of the loaded map. {@code mapCoordinates}
   * stores grid-cell offsets (each step is one tile = {@code MAP_SIZE} world units),
   * so the world max is {@code (gridOffset * MAP_SIZE) + (MAP_SIZE, 0, MAP_SIZE)}.
   *
   * @param arenaId the map to get the bounds for
   * @return the maximum world-space bounds of the map
   */
  public Vec3d getMapBoundsMax(String arenaId) {
    final Vec3d min = getMapBoundsMin(arenaId);
    return new Vec3d(min.x + MAP_SIZE, min.y, min.z + MAP_SIZE);
  }

  /**
   * Returns the minimum world-space bounds of the loaded map (the bounds-min corner).
   * Converts the stored grid-cell offset to world units by multiplying by {@code MAP_SIZE}.
   *
   * @param map the map to get the bounds for
   * @return the minimum world-space bounds of the map
   */
  public Vec3d getMapBoundsMin(String map) {
    final Vec3d gridOffset = mapCoordinates.get(map);
    return new Vec3d(gridOffset.x * MAP_SIZE, gridOffset.y, gridOffset.z * MAP_SIZE);
  }

  /**
   * Unloads a given lvz-map. Block clearing runs asynchronously; the tracking
   * entries are removed after the clear completes so a subsequent loadMap for
   * the same slot will see it as occupied until the clear is done.
   *
   * @param mapName the name of the map to unload
   * @return true if the unload was queued, false if no such map is loaded
   */
  public boolean unloadMap(final String mapName) {
    if (!activeMaps.containsKey(mapName)) {
      return false;
    }
    HashSet<Vec3d> coordinates = activeMaps.get(mapName);
    CompletableFuture<Boolean> completableFuture =
        CompletableFuture.supplyAsync(() -> this.removeBlocksFromLegacyMap(coordinates));
    completableFuture.thenAccept(s -> activeMaps.remove(mapName));
    completableFuture.thenAccept(s -> mapCoordinates.remove(mapName));
    return true;
  }

  /**
   * Replaces a loaded map with a new one at the same grid slot. The new map's
   * coordinate entry is registered synchronously (so bounds are immediately
   * queryable), while the old blocks are cleared and the new blocks are built
   * asynchronously.
   *
   * @param oldMapName the currently-loaded map to replace
   * @param newMapName the map to load in its place
   * @return true if the swap was queued, false if the old map isn't loaded or
   *         the new name has an invalid extension
   */
  public boolean swapMap(final String oldMapName, final String newMapName, final int arenaIndex) {
    if (!activeMaps.containsKey(oldMapName)) {
      return false;
    }
    if (!(newMapName.endsWith(".lvl") || newMapName.endsWith(".lvz"))) {
      return false;
    }
    Vec3d offset = mapCoordinates.get(oldMapName);
    TileId tile = TileId.fromCell((int) offset.x, 0, (int) offset.z);
    Vec3i corner = tile.getWorld(null);
    Vec3d worldOffset = new Vec3d(corner.x, corner.y, corner.z);

    HashSet<Vec3d> oldCoordinates = activeMaps.remove(oldMapName);
    mapCoordinates.remove(oldMapName);
    mapCoordinates.put(newMapName, offset);

    LevelFile res = (LevelFile) assetLoader.loadAsset(mapDirectory + "/" + newMapName);
    res.setMapName(newMapName);
    log.info("Swapping " + oldMapName + " -> " + newMapName + " at " + tile);

    final int tileBase = arenaTileBase(arenaIndex);
    final long createdTime = time != null ? time.getTime() : 0L;
    CompletableFuture
        .supplyAsync(() -> removeBlocksFromLegacyMap(oldCoordinates))
        .thenApplyAsync(s -> createBlocksFromLegacyMap(res, worldOffset, tileBase, createdTime))
        .thenAccept(blocks -> activeMaps.put(newMapName, blocks));
    return true;
  }

  /** Returns true if the given map is currently tracked as loaded. */
  public boolean isLoaded(final String mapName) {
    return activeMaps.containsKey(mapName);
  }

  private boolean removeBlocksFromLegacyMap(HashSet<Vec3d> coordinates) {
    for (Vec3d location : coordinates) {
      world.setWorldCell(location, 0);
    }
    verifyCleared(coordinates);
    return true;
  }

  /**
   * Post-clear audit: after iterating the tracked coordinates and writing 0, read
   * each cell back and report any whose type bits are still non-zero. A non-zero
   * readback means either our tracked set didn't cover a cell we wrote, or
   * another system re-filled the cell between clear and read.
   */
  private void verifyCleared(HashSet<Vec3d> coordinates) {
    int lingering = 0;
    int shown = 0;
    for (Vec3d location : coordinates) {
      int raw = world.getWorldCell(location);
      int type = raw & 0x000fffff;
      if (type != 0) {
        if (shown < 10) {
          log.warn(
              "Post-clear: cell still non-zero at " + location
                  + " type=" + type + " raw=0x" + Integer.toHexString(raw));
          shown++;
        }
        lingering++;
      }
    }
    if (lingering > 0) {
      log.warn("Post-clear verify: " + lingering + " / " + coordinates.size()
          + " cells still non-zero");
    } else {
      log.info("Post-clear verify: all " + coordinates.size() + " cells are zero");
    }
  }

  /**
   * Writes a legacy .lvl map into the world at the given offset. Cell-backed tiles
   * become a single world cell at Y=1; entity-backed tiles (flags, asteroids,
   * doors, wormholes) spawn their entity and leave the cell empty. Every
   * touched location (including entity positions) is returned so unload can
   * zero the slot cleanly.
   *
   * See {@code .claude/skills/lvl-format.md} for the tile-ID → semantic mapping
   * and {@link MapTypes} for the numeric constants.
   */
  public HashSet<Vec3d> createBlocksFromLegacyMap(
      final LevelFile map,
      final Vec3d arenaOffset,
      final int arenaTileBase,
      final long createdTime) {
    final HashSet<Vec3d> coordinates = new HashSet<>();
    final short[][] tiles = map.getMap();

    // --- Diagnostics: disposition counters ---
    int totalNonZero = 0;
    int turfFlags = 0;
    int asteroidsSmall = 0;
    int asteroidsMedium = 0;
    int over5 = 0;
    int doors = 0;
    int wormholes = 0;
    int cellsVisible = 0;
    int cellsInvisible = 0;
    int cellsFailedLeaf = 0;
    final java.util.TreeMap<Integer, Integer> idHistogram = new java.util.TreeMap<>();
    Vec3d firstWritten = null;
    Vec3d lastWritten = null;

    for (int xpos = 0; xpos < tiles.length; xpos++) {
      for (int zpos = 0; zpos < tiles[xpos].length; zpos++) {
        final short s = tiles[MAP_SIZE - xpos - 1][MAP_SIZE - zpos - 1];
        if (s == 0) {
          continue;
        }
        totalNonZero++;
        idHistogram.merge(Short.toUnsignedInt(s), 1, Integer::sum);

        final Vec3d location = new Vec3d(xpos, 1, zpos).add(arenaOffset);
        coordinates.add(location);

        if (s == MapTypes.vieTurfFlag) {
          GameEntities.createTurfStationaryFlag(
              ed, EntityId.NULL_ID, physicsSpace, createdTime, location);
          turfFlags++;
          continue;
        }
        if (s == MapTypes.vieAsteroidSmall) {
          GameEntities.createAsteroidSmall(ed, null, physicsSpace, createdTime, location, 0);
          asteroidsSmall++;
          continue;
        }
        if (s == MapTypes.vieAsteroidMedium) {
          GameEntities.createAsteroidMedium(ed, null, physicsSpace, createdTime, location, 0);
          asteroidsMedium++;
          continue;
        }
        if (s == MapTypes.vieAsteroidEnd) {
          GameEntities.createOver5(ed, null, physicsSpace, createdTime, location);
          over5++;
          continue;
        }
        if (s >= MapTypes.vieVDoorStart && s <= MapTypes.vieHDoorEnd) {
          GameEntities.createDoor(ed, null, physicsSpace, createdTime, 5000, location);
          doors++;
          continue;
        }
        if (s == MapTypes.vieWormhole) {
          GameEntities.createWormhole(
              ed, null, physicsSpace, createdTime, location,
              5000, GravityWell.PULL, new Vec3d(0, 0, 0), 1);
          wormholes++;
          continue;
        }

        final int tileId = Short.toUnsignedInt(s);
        final int blockType = (tileId >= 1 && tileId <= MAX_VISIBLE_TILE)
            ? arenaTileBase + tileId - 1
            : INVISIBLE_BLOCK_TYPE;
        final int result = world.setWorldCell(location, blockType);
        if (result == -1) {
          cellsFailedLeaf++;
        } else if (blockType == INVISIBLE_BLOCK_TYPE) {
          cellsInvisible++;
        } else {
          cellsVisible++;
        }
        if (firstWritten == null) {
          firstWritten = location;
        }
        lastWritten = location;
      }
    }

    log.info(
        "createBlocksFromLegacyMap: map={} offset={} nonZero={} (visibleCells={} invisibleCells={} leafFailures={})",
        map.getMapName(),
        arenaOffset,
        totalNonZero,
        cellsVisible,
        cellsInvisible,
        cellsFailedLeaf);
    log.info(
        "  entities: turfFlags={} asteroidsSmall={} asteroidsMedium={} over5={} doors={} wormholes={}",
        turfFlags,
        asteroidsSmall,
        asteroidsMedium,
        over5,
        doors,
        wormholes);
    if (firstWritten != null) {
      log.info("  first-written cell: {}    last-written cell: {}", firstWritten, lastWritten);
    }
    if (cellsFailedLeaf > 0) {
      log.warn(
          "  {}/{} cells silently dropped by setWorldCell (leaf==null). "
              + "Usually means the arena offset targets a world region whose leaves are not paged in.",
          cellsFailedLeaf,
          cellsVisible + cellsInvisible + cellsFailedLeaf);
    }
    if (log.isInfoEnabled()) {
      final StringBuilder sb = new StringBuilder("  tile-id histogram:");
      int shown = 0;
      for (final java.util.Map.Entry<Integer, Integer> e : idHistogram.entrySet()) {
        sb.append(" ").append(e.getKey()).append("=").append(e.getValue());
        if (++shown >= 40) {
          sb.append(" ...(").append(idHistogram.size() - shown).append(" more)");
          break;
        }
      }
      log.info(sb.toString());
    }

    spawnWallRunLights(tiles, arenaOffset, coordinates);

    return coordinates;
  }

  /**
   * Scans the map for straight wall runs of at least {@link CoreViewConstants#WALL_LIGHT_MIN_RUN}
   * tiles (horizontal or vertical) and writes light-emitter cells above them. The emitter cells
   * ({@link #LIGHT_EMITTER_BLOCK_TYPE}) have a packed light value on their BlockType that
   * {@code LightUtils.recalculateLighting} flood-fills into neighbor cells' lightData, which the
   * tile shader reads via vertex colors.
   *
   * <p>A "wall" is any tile id in the standard solid range
   * ({@code vieNormalStart..vieNormalEnd}); branches off the run are tolerated — only the straight
   * axis is measured.
   */
  private void spawnWallRunLights(final short[][] tiles, final Vec3d arenaOffset,
      final HashSet<Vec3d> coordinates) {
    final int sx = tiles.length;
    final int sz = tiles[0].length;
    final boolean[][] wall = new boolean[sx][sz];
    int wallTiles = 0;
    for (int x = 0; x < sx; x++) {
      for (int z = 0; z < sz; z++) {
        final short s = tiles[MAP_SIZE - x - 1][MAP_SIZE - z - 1];
        wall[x][z] = s >= MapTypes.vieNormalStart && s <= MapTypes.vieNormalEnd;
        if (wall[x][z]) {
          wallTiles++;
        }
      }
    }

    final int minRun = CoreViewConstants.WALL_LIGHT_MIN_RUN;
    final int spacing = Math.max(1, CoreViewConstants.WALL_LIGHT_SPACING);
    final int lightY = (int) Math.round(CoreViewConstants.WALL_LIGHT_PLANE_Y);

    int horizontalLights = 0;
    int verticalLights = 0;
    int longestHorizontal = 0;
    int longestVertical = 0;

    for (int z = 0; z < sz; z++) {
      int x = 0;
      while (x < sx) {
        if (wall[x][z] && (x == 0 || !wall[x - 1][z])) {
          int len = 0;
          while (x + len < sx && wall[x + len][z]) {
            len++;
          }
          if (len > longestHorizontal) {
            longestHorizontal = len;
          }
          if (len >= minRun) {
            final int count = Math.max(1, (int) Math.round((double) len / spacing));
            for (int i = 0; i < count; i++) {
              final int lightX = x + (int) Math.floor((i + 0.5) * len / count);
              final Vec3d pos = new Vec3d(lightX, lightY, z).add(arenaOffset);
              world.setWorldCell(pos, LIGHT_EMITTER_BLOCK_TYPE);
              coordinates.add(pos);
              horizontalLights++;
            }
          }
          x += Math.max(len, 1);
        } else {
          x++;
        }
      }
    }

    for (int x = 0; x < sx; x++) {
      int z = 0;
      while (z < sz) {
        if (wall[x][z] && (z == 0 || !wall[x][z - 1])) {
          int len = 0;
          while (z + len < sz && wall[x][z + len]) {
            len++;
          }
          if (len > longestVertical) {
            longestVertical = len;
          }
          if (len >= minRun) {
            final int count = Math.max(1, (int) Math.round((double) len / spacing));
            for (int i = 0; i < count; i++) {
              final int lightZ = z + (int) Math.floor((i + 0.5) * len / count);
              final Vec3d pos = new Vec3d(x, lightY, lightZ).add(arenaOffset);
              world.setWorldCell(pos, LIGHT_EMITTER_BLOCK_TYPE);
              coordinates.add(pos);
              verticalLights++;
            }
          }
          z += Math.max(len, 1);
        } else {
          z++;
        }
      }
    }

    log.info("Wall-run light emitters: wallTiles={} horiz={} vert={} (min run {}); "
            + "longest horiz={} longest vert={} at offset={}",
        wallTiles, horizontalLights, verticalLights, minRun,
        longestHorizontal, longestVertical, arenaOffset);
  }


  @Override
  protected void terminate() {
    // TODO Auto-generated method stub
  }

  @Override
  public void update(final SimTime tpf) {
    time = tpf;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}


  private enum Direction {
    E(1, 0) {
      Direction next() {
        return N;
      }
    },
    N(0, 1) {
      Direction next() {
        return W;
      }
    },
    W(-1, 0) {
      Direction next() {
        return S;
      }
    },
    S(0, -1) {
      Direction next() {
        return E;
      }
    };
    private final int dx;
    private final int dz;

    Direction(int dx, int dz) {
      this.dx = dx;
      this.dz = dz;
    }

    Vec3d advance(Vec3d point) {
      return new Vec3d(point.x + dx, 0, point.z + dz);
    }

    abstract Direction next();
  }

}
