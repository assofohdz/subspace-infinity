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
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.World;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.GravityWell;
import infinity.es.TileTypes;
import infinity.map.LevelFile;
import infinity.map.LevelLoader;
import infinity.server.AssetLoaderService;
import infinity.server.chat.ChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandConsumer;
import infinity.sim.GameEntities;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
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
  private static final int HALF = MAP_SIZE / 2;
  static Logger log = LoggerFactory.getLogger(MapSystem.class);
  private final String mapDirectory = "maps";
  private final Pattern loadMap = Pattern.compile("\\~loadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern unloadMap = Pattern.compile("\\~unloadMap\\s(\\w+.(?:lvl|lvz))");
  private final LinkedHashSet<Vec3d> sessionTileRemovals = new LinkedHashSet<>();
  private final LinkedHashSet<Vec3d> sessionTileCreations = new LinkedHashSet<>();
  // Map that holds all block coordinates for a given map:
  private final HashMap<String, HashSet<Vec3d>> activeMaps = new HashMap<>();
  // Map that holds the offset coordinates of each map:
  private final LinkedHashMap<String, Vec3d> mapCoordinates = new LinkedHashMap<>();
  private final boolean mapCreated = false;
  private ChatHostedService chat;
  private Vec3d currentMapLoc = new Vec3d();
  private EntityData ed;
  private MPhysSystem<MBlockShape> physics;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private SimTime time;
  private EntitySet tileTypes;
  private AssetLoaderService assetLoader;
  private LinkedList<MapTileCallable> mapTileQueue;
  private World world;
  private int y;
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
    this.chat = getSystem(ChatHostedService.class);
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

    // Register consuming methods for patterns
    chat.registerPatternBiConsumer(
        loadMap,
        "The command to load a new map is ~loadMap <mapName>, where <mapName> is the name "
            + "of the map you want to load",
        new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, map) -> loadMap(id, map)));
    chat.registerPatternBiConsumer(
        unloadMap,
        "The command to unload a new map is ~unloadMap <mapName>, where <mapName> is the "
            + "name of the map you want to unload",
        new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, map) -> unloadMap(id, map)));
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

    return new Vec3d(centerOfArenaX, 0, centerOfArenaZ);
  }

  private Vec3d calculateNextOffset() {
    log.info("Currentmap location is:" + currentMapLoc + ", current direction is:" + direction);
    Direction testDirection = direction.next();
    log.debug("Direction: " + direction + ", testDirection:" + testDirection);
    Vec3d testMapLoc = testDirection.advance(currentMapLoc);
    log.debug("testMapLoc: " + testMapLoc);

    // First time we will land here:
    if (!mapCoordinates.containsValue(currentMapLoc)) {
      return currentMapLoc;
    } else if (!mapCoordinates.containsValue(testMapLoc)) {
      // Test if we should go new direction
      currentMapLoc = testMapLoc;
      direction = testDirection;
      return currentMapLoc;
    }

    // If we have to continue straight ahead in our direction:
    currentMapLoc = direction.advance(currentMapLoc);
    return currentMapLoc;
  }

  /**
   * Loads a given lvz-map.
   *
   * @param id the entity requesting the load
   * @param mapName the lvz-map to load
   * @return the lvz-map wrapped in a LevelFile
   */
  public boolean loadMap(EntityId id, final String mapName) {
    log.info("Loading map: " + mapName);
    if (!(mapName.endsWith(".lvl") || mapName.endsWith(".lvz"))) {
      return false;
    }
    String fileName = mapDirectory + "/" + mapName;
    LevelFile res = (LevelFile) assetLoader.loadAsset(fileName);
    // The offset we get will be local based map location (0,0), (0,1), (1,0) etc. so we multiply by
    // mapsize
    Vec3d offset = calculateNextOffset();
    Vec3d worldOffset = offset.mult(MAP_SIZE);
    // FIXME add the space between maps of 1 cell size
    log.info("Loading map at local coords: " + currentMapLoc + " in world:" + offset);

    // HashSet<Vec3d> coordinates = this.createBlocksFromLegacyMap(res,offset);

    // Try to do this asynchronosly (loading a map takes about 3 seconds depending on density of
    // map)
    CompletableFuture<HashSet<Vec3d>> completableFuture =
        CompletableFuture.supplyAsync(() -> this.createBlocksFromLegacyMap(res, worldOffset));
    // CompletableFuture<Void> future =
    completableFuture.thenAccept(s -> activeMaps.put(mapName, s));
    //

    res.setMapName(mapName);
    log.info("Done loading map: " + mapName);
    // activeMaps.put(mapName, coordinates);
    mapCoordinates.put(mapName, offset);

    return true;
  }

  public Vec3d getMapBoundsMax(String arenaId) {
    Vec3d mapOffset = mapCoordinates.get(arenaId);
    Vec3d mapBoundsMax = mapOffset.add(MAP_SIZE, 0, MAP_SIZE);
    return mapBoundsMax;
  }

  public Vec3d getMapBoundsMin(String map) {
    Vec3d mapOffset = mapCoordinates.get(map);
    return mapOffset;
  }

  /**
   * Unloads a given lvz-map.
   *
   * @param id the entity requesting the unload
   * @param mapName the lvz-map to load
   * @return true if unloaded, false otherwise
   */
  public boolean unloadMap(EntityId id, final String mapName) {
    if (!activeMaps.containsKey(mapName)) {
      return false;
    }
    HashSet<Vec3d> coordinates = activeMaps.get(mapName);
    // Try to do this asynchronosly (loading a map takes about 3 seconds depending on density of
    // map)
    CompletableFuture<Boolean> completableFuture =
        CompletableFuture.supplyAsync(() -> this.removeBlocksFromLegacyMap(coordinates));
    // CompletableFuture<Void> future =
    completableFuture.thenAccept(s -> activeMaps.remove(mapName));
    completableFuture.thenAccept(s -> mapCoordinates.remove(mapName));

    return true;
  }

  private boolean removeBlocksFromLegacyMap(HashSet<Vec3d> coordinates) {
    for (Vec3d location : coordinates) {
      world.setWorldCell(location, 0);
    }
    return true;
  }

  /**
   * Creates map tiles for a legacy map.
   *
   * @param map the lvz-based-map to create create entities from
   * @param arenaOffset where to position the map
   */
  public HashSet<Vec3d> createBlocksFromLegacyMap(final LevelFile map, final Vec3d arenaOffset) {
    final Set<Integer> tileSet = new HashSet<>();

    HashSet<Vec3d> coordinates = new HashSet<>();

    final short[][] tiles = map.getMap();
    // int count = 0;

    // for (int xpos = 510; xpos < 516; xpos++) {
    for (int xpos = 0; xpos < tiles.length; xpos++) {
      // for (int zpos = 260; zpos >= 250; zpos--) {
      for (int zpos = 0; zpos < tiles[xpos].length; zpos++) {
        short s = tiles[1024 - xpos - 1][1024 - zpos - 1];
        y = 1;
        // I'd like a small part of the corners of the map to be cleared so we can move in and out
        // of arenas
        if ((xpos == 0 || xpos == 1 || xpos == 2) && (zpos == 0 || zpos == 1 || zpos == 2)) {
          //s = 0;
          y = 5;
        } else if ((xpos == 1021 || xpos == 1022 || xpos == 1023)
            && (zpos == 0 || zpos == 1 || zpos == 2)) {
          y = 5;
        } else if ((xpos == 0 || xpos == 1 || xpos == 2)
            && (zpos == 1021 || zpos == 1022 || zpos == 1023)) {
          y = 5;
        } else if ((xpos == 1021 || xpos == 1022 || xpos == 1023)
            && (zpos == 1021 || zpos == 1022 || zpos == 1023)) {
          y = 5;
        } else if (xpos == 0
            || zpos == 0
            || xpos == tiles.length - 1
            || zpos == tiles[xpos].length - 1) {
          // Set arena boundaries
          s = 1;
        }

        if (s != 0) {
          // TODO: Check on the short and only create the map tiles, not the extras
          // (asteroids, wormholes etc.)
          /*
           * TILE STATUS Row 2, tile 1 - Border tile Row 9, tile 10 - Vertical warpgate
           * (Mostly open) Row 9, tile 11 - Vertical warpgate (Frequently open) Row 9,
           * tile 12 - Vertical warpgate (Frequently closed) Row 9, tile 13 - Vertical
           * warpgate (Mostly closed) Row 9, tile 14 - Horizontal warpgate (Mostly open)
           * Row 9, tile 15 - Horizontal warpgate (Frequently open) Row 9, tile 16 -
           * Horizontal warpgate (Frequently closed) Row 9, tile 17 - Horizontal warpgate
           * (Mostly closed) 170 DONE Row 9, tile 18 - Flag for turf Row 9, tile 19 -
           * Safezone Row 10, tile 1 - Soccer goal (leave blank if you want) Row 10, tile
           * 2 - Flyover tile Row 10, tile 3 - Flyover tile Row 10, tile 4 - Flyover tile
           * Row 10, tile 5 - Flyunder (opaque) tile Row 10, tile 6 - Flyunder (opaque)
           * tile Row 10, tile 7 - Flyunder (opaque) tile Row 10, tile 8 - Flyunder
           * (opaque) tile Row 10, tile 9 - Flyunder (opaque) tile Row 10, tile 10 -
           * Flunder (opaque) tile Row 10, tile 11 - Flyunder (opaque) tile Row 10, tile
           * 12 - Flyunder (opaque) tile Row 10, tile 13 - Flyunder (black = transparent)
           * tile Row 10, tile 14 - Flyunder (black = transparent) tile Row 10, tile 15 -
           * Flyunder (black = transparent) tile Row 10, tile 16 - Flyunder (black =
           * transparent) tile Row 10, tile 17 - Flyunder (black = transparent) tile Row
           * 10, tile 18 - Flyunder (black = transparent) tile Row 10, tile 19 - Flyunder
           * (black = transparent) tile
           *
           * /* VIE tile constants.
           *
           * public static final char vieNoTile = 0;
           *
           * public static final char vieNormalStart = 1; public static final char
           * vieBorder = 20; // Borders are not included in the .lvl files public static
           * final char vieNormalEnd = 161; // Tiles up to this point are part of sec.chk
           *
           * public static final char vieVDoorStart = 162; public static final char
           * vieVDoorEnd = 165;
           *
           * public static final char vieHDoorStart = 166; public static final char
           * vieHDoorEnd = 169;
           *
           * public static final char vieTurfFlag = 170;
           *
           * public static final char vieSafeZone = 171; // Also included in sec.chk
           *
           * public static final char vieGoalArea = 172;
           *
           * public static final char vieFlyOverStart = 173; public static final char
           * vieFlyOverEnd = 175; public static final char vieFlyUnderStart = 176; public
           * static final char vieFlyUnderEnd = 190;
           *
           * public static final char vieAsteroidStart = 216; public static final char
           * vieAsteroidEnd = 218;
           *
           * public static final char vieStation = 219;
           *
           * public static final char vieWormhole = 220;
           *
           * public static final char ssbTeamBrick = 221; // These are internal public
           * static final char ssbEnemyBrick = 222;
           *
           * public static final char ssbTeamGoal = 223; public static final char
           * ssbEnemyGoal = 224;
           *
           * public static final char ssbTeamFlag = 225; public static final char
           * ssbEnemyFlag = 226;
           *
           * public static final char ssbPrize = 227;
           *
           * public static final char ssbBorder = 228; // Use ssbBorder instead of
           * vieBorder to fill border
           *
           * 20: Border 162: Door Horizontal 1 163: Door Horizontal 2 164: Door Horizontal
           * 3 165: Door Horizontal 4 166: Door Vertical 1 167: Door Vertical 2 168: Door
           * Vertical 3 169: Door Vertical 4 170: flag 171: safe 172: goal 173: fly over 1
           * 174: fly over 2 175: fly over 3 176: fly Under 1 177: fly Under 2 178: fly
           * Under 3 179: fly Under 4 180: fly Under 5 181: fly Under 6 182: fly Under 7
           * 183: fly Under 8 184: fly Under 9 185: fly Under 10 186: fly Under 11 187:
           * fly Under 12 188: fly Under 13 189: fly Under 14 190: fly Under 15 191:
           * invisible, Ships go through, items bounce off, Thors go through if you fire
           * an item while in it, it will float suspended in space. 192: invisible 193:
           * invisible 194: invisible 195: invisible 196: invisible 197: invisible 198:
           * invisible 199: invisible 200: invisible 201: invisible 202: invisible 203:
           * invisible 204: invisible 205: invisible 206: invisible 207: invisible 216:
           * small Asteroid 217: large Asteroid 218: small Asteroid 2 219: space Station
           * 220: wormhole 240: invisible 241: absorbs weapons, invisible 242: warp on
           * contact, not on radar, invisible 242: not on radar, invisible 243: not on
           * radar, invisible 244: not on radar, invisible 245: not on radar, invisible
           * 246: not on radar, invisible 247: not on radar, invisible 248: not on radar,
           * invisible 249: not on radar, invisible 250: not on radar, invisible 251:
           * invisible, not on radar, warps ship on contact, items bounce off, thors
           * dissappear 252: animated enemy brick, visible, not on radar. Items go
           * through, ship gets warped after 0-2 seconds 253: animated team brick.
           * Visible, invisible on radar. Items and ship go through. 254: invisible, not
           * on radar. Impossible to lay bricks while on/near it. 255: animated green.
           * visible, not on radar. Items and ship go through.
           *
           */

          final Vec3d location = new Vec3d(xpos, y, zpos).add(arenaOffset);
          coordinates.add(location);
          // TODO: add more special cases here:
          // TODO: Fetch settings for the given coordinates and create the right gravity
          switch (s) {
              // turf flag
            case 170:
              GameEntities.createCaptureTheFlag(ed, null, physicsSpace, time.getTime(), location);
              break;
              // small asteroid
            case 216:
              GameEntities.createAsteroidSmall(ed, null, physicsSpace, time.getTime(), location, 0);
              break;
              // large asteroid
            case 217:
              GameEntities.createAsteroidMedium(
                  ed, null, physicsSpace, time.getTime(), location, 0);
              break;
              // small Asteroid 2
            case 218:
              GameEntities.createAsteroidLarge(ed, null, physicsSpace, time.getTime(), location);
              break;
              // wormhole
            case 220:
              GameEntities.createWormhole(
                  ed,
                  null,
                  physicsSpace,
                  time.getTime(),
                  location,
                  5,
                  5,
                  5,
                  GravityWell.PULL,
                  new Vec3d(0, 0, 0));
              break;
            default:
              // TODO: Translate mapId from a level name (see InfinityBlockGeometryIndex)
              final int mapId = 20;
              final int tileId = Short.toUnsignedInt(s);
              tileSet.add(Integer.valueOf(tileId));

              final int value = tileId | (mapId << 8);
              world.setWorldCell(location, 10);
              break;
          }

          // Vec3d topPlane = new Vec3d(xpos, 1, -zpos).add(arenaOffset);

          // final Vec3i i = new Vec3i(bottomPlane.toVector3f());

          //
          //                    //TODO: Translate mapId from a level name (see
          // InfinityBlockGeometryIndex)
          //                    final int mapId = 20;
          //                    final int tileId = Short.toUnsignedInt(s);
          //                    tileSet.add(Integer.valueOf(tileId));
          //
          //                    final int value = tileId | (mapId << 8);
          //                    // log.info("createEntitiesFromLegacyMap:: value = " + value + " <=
          // (Tile,Map)
          //                    // =(" + tileId + ","
          //                    // + mapId + ") - Coords: " + i);
          //                    // value = InfinityMaskUtils.setSideMask(value,
          // DirectionMasks.UP_MASK);
          //                    // world.setWorldCell(bottomPlane, value);
          //                    //log.info("Creating block at: " + location);
          //                    world.setWorldCell(location, 10);
          //
          //                    // world.setWorldCell(topPlane, 0);
          //
          //                    // count++;
        }
      }
    }

    // Test:
    /*
     * for (int i = 0; i < 380; i++) { short s = (short) (i % 190); Vec3d
     * bottomPlane = new Vec3d(i % 19, 0, (Math.floor(i / 19))); Vec3d topPlane =
     * new Vec3d(i % 19, 1, (Math.floor(i / 19))); int mapId = 20; int tileId =
     * Short.toUnsignedInt(s); tileSet.add(tileId);
     *
     * int value = tileId | (mapId << 8);
     * log.info("createEntitiesFromLegacyMap:: value = " + value +
     * " <= (Tile,Map) = (" + tileId + "," + mapId + ") - Coords: " + bottomPlane);
     * world.setWorldCell(bottomPlane, value); world.setWorldCell(topPlane, 0);
     * count++; }
     */

    return coordinates;
  }

  @Override
  protected void terminate() {
    // Release reader object
    // reader = null;

    tileTypes.release();
    tileTypes = null;
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
