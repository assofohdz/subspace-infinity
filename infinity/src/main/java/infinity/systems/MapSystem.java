/*
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.systems;

import com.github.czyzby.noise4j.map.Grid;
import com.github.czyzby.noise4j.map.generator.room.RoomType.DefaultRoomType;
import com.github.czyzby.noise4j.map.generator.room.dungeon.DungeonGenerator;
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
import infinity.es.GravityWell;
import infinity.es.TileTypes;
import infinity.map.LevelFile;
import infinity.map.LevelLoader;
import infinity.server.AssetLoaderService;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.GameEntities;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.concurrent.Callable;
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
  public static final int MAP_SIZE = 1024;
  public static final float NOISE4J_CORRIDOR = 0f;
  public static final float NOISE4J_FLOOR = 0.5f;
  public static final float NOISE4J_WALL = 1f;

  /**
   * Base block type index for flat 2D tiles. Tile N uses type (TILE_TYPE_BASE + N - 1). This must
   * match the value in BlockGeometryIndex.
   */
  public static final int TILE_TYPE_BASE = 100;

  /** Maximum tile ID for visible tiles (1-190 in Subspace). */
  public static final int MAX_VISIBLE_TILE = 190;

  /**
   * Block type index for invisible physics blocks. These blocks have collision but no visible
   * geometry. Must match BlockGeometryIndex.INVISIBLE_BLOCK_TYPE_INDEX.
   */
  public static final int INVISIBLE_BLOCK_TYPE = 11;

  private static final int HALF = MAP_SIZE / 2;
  static Logger log = LoggerFactory.getLogger(MapSystem.class);
  private final String mapDirectory = "Maps";
  private final LinkedHashSet<Vec3d> sessionTileRemovals = new LinkedHashSet<>();
  private final LinkedHashSet<Vec3d> sessionTileCreations = new LinkedHashSet<>();
  // Map that holds all block coordinates for a given map:
  private final HashMap<String, HashSet<Vec3d>> activeMaps = new HashMap<>();
  // Map that holds the offset coordinates of each map:
  private final LinkedHashMap<String, Vec3d> mapCoordinates = new LinkedHashMap<>();
  private final boolean mapCreated = false;
  private InfinityChatHostedService chat;
  private Vec3d currentMapLoc = new Vec3d(-1, 0, -1);
  private EntityData ed;
  private MPhysSystem<MBlockShape> physics;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private SimTime time;
  // private EntitySet tileTypes;
  private AssetLoaderService assetLoader;
  private LinkedList<MapTileCallable> mapTileQueue;
  private World world;
  private double accumulatedTime;
  // private final boolean logged = false;
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
    this.chat = getSystem(InfinityChatHostedService.class);
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

    /*
     * Grid dungeon = this.createDungeonGrid(); dungeon =
     * this.expandCorridors(dungeon); this.createMapTilesFromDungeonGrid(dungeon,
     * -50f, -50f);
     */
    mapTileQueue = new LinkedList<>();
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
   * @param mapName the lvz-map to load
   * @return true if loaded
   */
  public boolean loadMap(final String mapName) {
    if (!(mapName.endsWith(".lvl") || mapName.endsWith(".lvz"))) {
      return false;
    }
    Vec3d offset = calculateNextOffset();
    TileId tile = TileId.fromCell((int) offset.x, 0, (int) offset.z);
    return loadMap(mapName, tile);
  }

  /**
   * Loads a map at an explicit grid location. Each TileId is a 1024x1024 slot
   * on Moss's TILE_GRID; adjacent tiles share a 2-cell gutter formed by each
   * map's own border ring.
   *
   * @param mapName the lvz-map to load
   * @param tile the Moss TileId specifying where to place the map
   * @return true if loaded, false if the filename is invalid or the tile is occupied
   */
  public boolean loadMap(final String mapName, final TileId tile) {
    log.info("Loading map: " + mapName + " at " + tile);
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

    // Loading a map takes ~3 seconds depending on density — do it asynchronously.
    CompletableFuture<HashSet<Vec3d>> completableFuture =
        CompletableFuture.supplyAsync(() -> this.createBlocksFromLegacyMap(res, worldOffset));
    completableFuture.thenAccept(s -> activeMaps.put(mapName, s));

    res.setMapName(mapName);
    mapCoordinates.put(mapName, offset);
    log.info("Queued map: " + mapName + " at grid " + offset + " (world " + worldOffset + ")");
    return true;
  }

  /**
   * Returns the maximum bounds of a given map.
   *
   * @param arenaId the map to get the bounds for
   * @return the maximum bounds of the map
   */
  public Vec3d getMapBoundsMax(String arenaId) {
    Vec3d mapOffset = mapCoordinates.get(arenaId);
    Vec3d mapBoundsMax = mapOffset.add(MAP_SIZE, 0, MAP_SIZE);
    return mapBoundsMax;
  }

  /**
   * Returns the minimum bounds of a given map.
   *
   * @param map the map to get the bounds for
   * @return the minimum bounds of the map
   */
  public Vec3d getMapBoundsMin(String map) {
    Vec3d mapOffset = mapCoordinates.get(map);
    return mapOffset;
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
  public boolean swapMap(final String oldMapName, final String newMapName) {
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

    CompletableFuture
        .supplyAsync(() -> removeBlocksFromLegacyMap(oldCoordinates))
        .thenApplyAsync(s -> createBlocksFromLegacyMap(res, worldOffset))
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
  public HashSet<Vec3d> createBlocksFromLegacyMap(final LevelFile map, final Vec3d arenaOffset) {
    HashSet<Vec3d> coordinates = new HashSet<>();
    short[][] tiles = map.getMap();

    for (int xpos = 0; xpos < tiles.length; xpos++) {
      for (int zpos = 0; zpos < tiles[xpos].length; zpos++) {
        short s = tiles[MAP_SIZE - xpos - 1][MAP_SIZE - zpos - 1];
        if (s == 0) {
          continue;
        }

        Vec3d location = new Vec3d(xpos, 1, zpos).add(arenaOffset);
        coordinates.add(location);

        if (s == MapTypes.vieTurfFlag) {
          GameEntities.createTurfStationaryFlag(
              ed, EntityId.NULL_ID, physicsSpace, time.getTime(), location);
          continue;
        }
        if (s == MapTypes.vieAsteroidSmall) {
          GameEntities.createAsteroidSmall(ed, null, physicsSpace, time.getTime(), location, 0);
          continue;
        }
        if (s == MapTypes.vieAsteroidMedium) {
          GameEntities.createAsteroidMedium(ed, null, physicsSpace, time.getTime(), location, 0);
          continue;
        }
        if (s == MapTypes.vieAsteroidEnd) {
          GameEntities.createWormhole2(ed, null, physicsSpace, time.getTime(), location);
          continue;
        }
        if (s >= MapTypes.vieVDoorStart && s <= MapTypes.vieHDoorEnd) {
          GameEntities.createDoor(ed, null, physicsSpace, time.getTime(), 5000, location);
          continue;
        }
        if (s == MapTypes.vieWormhole) {
          GameEntities.createWormhole(
              ed, null, physicsSpace, time.getTime(), location,
              5000, GravityWell.PULL, new Vec3d(0, 0, 0), 1);
          continue;
        }

        int tileId = Short.toUnsignedInt(s);
        int blockType = (tileId >= 1 && tileId <= MAX_VISIBLE_TILE)
            ? TILE_TYPE_BASE + tileId - 1
            : INVISIBLE_BLOCK_TYPE;
        world.setWorldCell(location, blockType);
      }
    }

    return coordinates;
  }


  @Override
  protected void terminate() {
    // TODO Auto-generated method stub
  }

  @Override
  public void update(final SimTime tpf) {

    time = tpf;
    accumulatedTime += tpf.getTpf();

    // Create map:
    // if (!mapCreated && accumulatedTime > 2) {
    // TODO: See InfinityBlockGeometryIndex - same map name is used there to translate back
    // createEntitiesFromLegacyMap(loadMap(trenchMap), new Vec3d(-MAP_SIZE * 0.5, 0, -MAP_SIZE *
    // 0.5));
    // createEntitiesFromLegacyMap(loadMap("Maps/tunnelbase.lvl"), new
    // Vec3d(-MAP_SIZE, 0, MAP_SIZE));
    // createEntitiesFromLegacyMap(loadMap("Maps/trench.lvl"), new
    // Vec3d(-HALF,HALF,0 , 0));
    // createEntitiesFromMap(loadMap("Maps/turretwarz.lvl"), new
    // Vec3d(0,MAP_SIZE,0,0));
    // mapCreated = true;
    // }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  /**
   * Returns a tile location key based on a Vec3d.
   *
   * @param location the Vec3d to clamp
   * @return the clamped Vec3d location
   */
  private Vec3d getKey(final Vec3d location) {
    final Vec3d coordinates =
        new Vec3d(Math.round(location.x - 0.5) + 0.5, 0, Math.round(location.z - 0.5) + 0.5);
    return coordinates;
  }

  /**
   * Queue up a tile for removal.
   *
   * @param x the x-coordinate
   * @param z the y-coordinate
   */
  public void sessionRemoveTile(final double x, final double z) {
    final Vec3d clampedLocation = getKey(new Vec3d(x, 0, z));
    sessionTileRemovals.add(clampedLocation);
  }

  /**
   * Queue up a tile for creation.
   *
   * @param x the x-coordinate
   * @param z the y-coordinate
   */
  public void sessionCreateTile(final double x, final double z) {
    final Vec3d clampedLocation = getKey(new Vec3d(x, 0, z));
    sessionTileCreations.add(clampedLocation);
  }

  @SuppressWarnings("unused")
  private Grid createDungeonGrid() {
    final Grid result = new Grid(100, 100);

    final DungeonGenerator dungeonGenerator = new DungeonGenerator();

    dungeonGenerator.setCorridorThreshold(NOISE4J_CORRIDOR);
    dungeonGenerator.setFloorThreshold(NOISE4J_FLOOR);
    dungeonGenerator.setWallThreshold(NOISE4J_WALL);

    dungeonGenerator.setRoomGenerationAttempts(100);
    dungeonGenerator.setMaxRoomsAmount(10);
    dungeonGenerator.addRoomTypes(DefaultRoomType.values());

    // Max first, then min. Only odd values
    dungeonGenerator.setMaxRoomSize(21);
    dungeonGenerator.setMinRoomSize(9);

    dungeonGenerator.generate(result);

    // result = carveCorridors(result);
    return result;
  }

  /**
   * Expands the corridors of a (dungeon) Grid by one.
   *
   * @param grid the Grid to expand corridors in
   * @return the new Grid with expanded corridors
   */
  @SuppressWarnings("unused")
  private Grid expandCorridors(final Grid grid) {
    final Grid newGrid = grid.copy();

    for (int i = 0; i < grid.getWidth(); i++) {
      for (int j = 0; j < grid.getHeight(); j++) {
        if (grid.get(i, j) == NOISE4J_CORRIDOR) {
          // newGrid.set(i - 1, j - 1, 0f);
          // newGrid.set(i - 1, j, 0f);
          // newGrid.set(i - 1, j + 1, 0f);
          // newGrid.set(i + 1, j - 1, 0f);

          if (grid.get(i + 1, j) == NOISE4J_WALL && grid.get(i + 1, j) != NOISE4J_FLOOR) {
            newGrid.set(i + 1, j, NOISE4J_CORRIDOR);
          }
          if (grid.get(i + 1, j + 1) == NOISE4J_WALL && grid.get(i + 1, j + 1) != NOISE4J_FLOOR) {
            newGrid.set(i + 1, j + 1, NOISE4J_CORRIDOR);
          }
          if (grid.get(i, j + 1) == NOISE4J_WALL && grid.get(i, j + 1) != NOISE4J_FLOOR) {
            newGrid.set(i, j + 1, NOISE4J_CORRIDOR);
          }
          // newGrid.set(i, j - 1, 0f);
          // newGrid.set(i, j, 0f);
        }
      }
    }
    grid.set(newGrid);

    return grid;
  }

  /*
   * So I dont forget:
   *
   * <p>Go through grid, carve all corridors to be 2 wide in a copy Assign copy to grid Go through
   * grid, set all tiles adjacent to floor or corridor to wall Create one or more entrances
   */

  /**
   * Creates map tiles from a Dungeon Grid. Default values: wallThreshold = 1f; floorThreshold =
   * 0.5f; corridorThreshold = 0f; Use the statics NOISE4J_*.
   *
   * @param grid the Grid to create entities from
   * @param offsetX the offset x-coordinate to create the entities in
   * @param offsetZ the offset y-coordinate to create the entities in
   */
  @SuppressWarnings("unused")
  private void createMapTilesFromDungeonGrid(
      final Grid grid, final float offsetX, final float offsetZ) {
    float f;
    for (int i = 0; i < grid.getHeight(); i++) {
      for (int j = 0; j < grid.getWidth(); j++) {
        f = grid.get(j, i);
        if (f == 0f || f == 0.5f) {
          // Floors (rooms) && Corridors

          // Should create maptiles around rooms and corridors
          if (grid.get(j + 1, i) == 1f) {
            sessionCreateTile(j + 1 + offsetX, i + offsetZ);
          }
          if (grid.get(j - 1, i) == 1f) {
            sessionCreateTile(j - 1 + offsetX, i + offsetZ);
          }
          if (grid.get(j, i + 1) == 1f) {
            sessionCreateTile(j + offsetX, i + 1 + offsetZ);
          }
          if (grid.get(j, i - 1) == 1f) {
            sessionCreateTile(j + offsetX, i - 1 + offsetZ);
          }
          if (grid.get(j + 1, i + 1) == 1f) {
            sessionCreateTile(j + 1 + offsetX, i + 1 + offsetZ);
          }
          if (grid.get(j - 1, i + 1) == 1f) {
            sessionCreateTile(j - 1 + offsetX, i + 1 + offsetZ);
          }
          if (grid.get(j + 1, i - 1) == 1f) {
            sessionCreateTile(j + 1 + offsetX, i - 1 + offsetZ);
          }
          if (grid.get(j - 1, i - 1) == 1f) {
            sessionCreateTile(j - 1 + offsetX, i - 1 + offsetZ);
          }
        }
      }
    }
  }

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

  private static final class MapTileCallable implements Callable<EntityId> {

    String mFile;
    short s;
    Vec3d loc;
    String type;
    EntityData ed;
    long time;
    PhysicsSpace<EntityId, MBlockShape> space;

    @SuppressWarnings("unused")
    public MapTileCallable(
        final String file,
        final short s,
        final Vec3d location,
        final String type,
        final EntityData ed,
        final long time,
        final PhysicsSpace<EntityId, MBlockShape> space) {
      this.mFile = file;
      this.s = s;
      loc = location;
      this.type = type;
      this.ed = ed;
      this.time = time;
      this.space = space;
    }

    @Override
    public EntityId call() throws Exception {
      // EntityId id = GameEntities.createMapTile(m_file, s, loc, type, ed, time);

      final EntityId id =
          GameEntities.createMapTile(ed, EntityId.NULL_ID, space, time, mFile, s, loc, type);

      log.debug("Called up creation of entity: " + id + ". " + this);
      return id;
    }

    @Override
    public String toString() {
      String sb =
          "MapTileCallable{m_file="
              + mFile
              + ", s="
              + s
              + ", loc="
              + loc
              + ", type="
              + type
              + ", ed="
              + ed
              + ", time="
              + time
              + '}';
      return sb;
    }
  }
}
