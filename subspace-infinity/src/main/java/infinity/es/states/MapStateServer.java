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
package infinity.es.states;

import com.github.czyzby.noise4j.map.Grid;
import com.github.czyzby.noise4j.map.generator.room.RoomType.DefaultRoomType;
import com.github.czyzby.noise4j.map.generator.room.dungeon.DungeonGenerator;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.GravityWell;
import infinity.api.es.Position;
import infinity.api.es.TileType;
import infinity.api.es.TileTypes;
import infinity.api.sim.ModuleGameEntities;
import infinity.map.LevelFile;
import infinity.map.LevelLoader;
import infinity.net.server.AssetLoaderService;
import java.lang.reflect.Method;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.dyn4j.geometry.Vector2;
import org.mapeditor.io.TMXMapReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State
 *
 * @author Asser
 */
public class MapStateServer extends AbstractGameSystem {
    
    static Logger log = LoggerFactory.getLogger(MapStateServer.class);

    private TMXMapReader reader;
    private EntityData ed;
    private java.util.Map<Vector2, EntityId> index = new ConcurrentHashMap<>();
    public static final int MAP_SIZE = 1024;
    private static final int HALF = 512;
    private EntitySet tileTypes;

    private LinkedHashSet<Vector2> sessionTileRemovals = new LinkedHashSet<>();
    private LinkedHashSet<Vector2> sessionTileCreations = new LinkedHashSet<>();

    public static final float NOISE4J_CORRIDOR = 0f;
    public static final float NOISE4J_FLOOR = 0.5f;
    public static final float NOISE4J_WALL = 1f;
    private final AssetLoaderService assetLoader;
    private SimTime time;
    private boolean mapCreated = false;
    private LinkedList<MapTileCallable> mapTileQueue;

    public MapStateServer(AssetLoaderService assetLoader) {

        this.assetLoader = assetLoader;
    }

    //int[][] multD = new int[5][];
    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);

        assetLoader.registerLoader(LevelLoader.class, "lvl", "lvz");

        //Create entities, so the tile types will be in the string index (we use the tiletypes as filters)
        EntityId e = ed.createEntity();
        short s = 0;
        ed.setComponent(e, TileTypes.legacy("empty", s, ed));

        EntityId e2 = ed.createEntity();
        short s2 = 0;
        ed.setComponent(e2, TileTypes.wangblob("empty", s2, ed));

        tileTypes = ed.getEntities(TileType.class, Position.class);

        /*
        Grid dungeon = this.createDungeonGrid();
        dungeon = this.expandCorridors(dungeon);
        this.createMapTilesFromDungeonGrid(dungeon, -50f, -50f);
         */
        mapTileQueue = new LinkedList<>();
    }

    /**
     * Finds the center of an arena. Uses MAP_SIZE to calculate center
     *
     * @param currentXCoord the x-coordinate
     * @param currentYCoord the y-coordinate
     * @return the Vector2 center coordinate
     */
    public Vector2 getCenterOfArena(double currentXCoord, double currentYCoord) {
        double xArenaCoord = Math.floor(currentXCoord / MAP_SIZE);
        double yArenaCoord = Math.floor(currentYCoord / MAP_SIZE);

        double centerOfArenaX = currentXCoord < 0 ? xArenaCoord - HALF : xArenaCoord + HALF;
        double centerOfArenaY = currentYCoord < 0 ? yArenaCoord - HALF : yArenaCoord + HALF;

        return new Vector2(centerOfArenaX, centerOfArenaY);
    }

    /**
     * Loads a given lvz-map
     *
     * @param mapFile the lvz-map to load
     * @return the lvz-map wrapped in a LevelFile
     */
    public LevelFile loadMap(String mapFile) {
        LevelFile map = (LevelFile) assetLoader.loadAsset(mapFile);
        return map;
    }

    /**
     * Creates map tiles for a legacy map
     *
     * @param map the lvz-based-map to create create entities from
     * @param arenaOffset where to position the map
     */
    public void createEntitiesFromLegacyMap(LevelFile map, Vec3d arenaOffset) {
        short[][] tiles = map.getMap();

        for (int xpos = 0; xpos < tiles.length; xpos++) {
            for (int ypos = tiles[xpos].length - 1; ypos >= 0; ypos--) {
                short s = tiles[xpos][ypos];
                if (s != 0) {
                    //TODO: Check on the short and only create the map tiles, not the extras (asteroids, wormholes etc.)
/*  TILE    STATUS
                    Row 2, tile 1 - Border tile
                    Row 9, tile 10 - Vertical warpgate (Mostly open)
                    Row 9, tile 11 - Vertical warpgate (Frequently open)
                    Row 9, tile 12 - Vertical warpgate (Frequently closed)
                    Row 9, tile 13 - Vertical warpgate (Mostly closed)
                    Row 9, tile 14 - Horizontal warpgate (Mostly open)
                    Row 9, tile 15 - Horizontal warpgate (Frequently open)
                    Row 9, tile 16 - Horizontal warpgate (Frequently closed)
                    Row 9, tile 17 - Horizontal warpgate (Mostly closed)
     170    DONE    Row 9, tile 18 - Flag for turf
                    Row 9, tile 19 - Safezone
                    Row 10, tile 1 - Soccer goal (leave blank if you want)
                    Row 10, tile 2 - Flyover tile
                    Row 10, tile 3 - Flyover tile
                    Row 10, tile 4 - Flyover tile
                    Row 10, tile 5 - Flyunder (opaque) tile
                    Row 10, tile 6 - Flyunder (opaque) tile
                    Row 10, tile 7 - Flyunder (opaque) tile
                    Row 10, tile 8 - Flyunder (opaque) tile
                    Row 10, tile 9 - Flyunder (opaque) tile
                    Row 10, tile 10 - Flunder (opaque) tile
                    Row 10, tile 11 - Flyunder (opaque) tile
                    Row 10, tile 12 - Flyunder (opaque) tile
                    Row 10, tile 13 - Flyunder (black = transparent) tile
                    Row 10, tile 14 - Flyunder (black = transparent) tile
                    Row 10, tile 15 - Flyunder (black = transparent) tile
                    Row 10, tile 16 - Flyunder (black = transparent) tile
                    Row 10, tile 17 - Flyunder (black = transparent) tile
                    Row 10, tile 18 - Flyunder (black = transparent) tile
                    Row 10, tile 19 - Flyunder (black = transparent) tile

                    /*
                    VIE tile constants.

                    public static final char vieNoTile = 0;

                    public static final char vieNormalStart = 1;
                    public static final char vieBorder = 20;            // Borders are not included in the .lvl files
                    public static final char vieNormalEnd = 161;        // Tiles up to this point are part of sec.chk

                    public static final char vieVDoorStart = 162;
                    public static final char vieVDoorEnd = 165;

                    public static final char vieHDoorStart = 166;
                    public static final char vieHDoorEnd = 169;

                    public static final char vieTurfFlag = 170;

                    public static final char vieSafeZone = 171;         // Also included in sec.chk

                    public static final char vieGoalArea = 172;

                    public static final char vieFlyOverStart = 173;
                    public static final char vieFlyOverEnd = 175;
                    public static final char vieFlyUnderStart = 176;
                    public static final char vieFlyUnderEnd = 190;

                    public static final char vieAsteroidStart = 216;
                    public static final char vieAsteroidEnd = 218;

                    public static final char vieStation = 219;

                    public static final char vieWormhole = 220;

                    public static final char ssbTeamBrick = 221;        // These are internal
                    public static final char ssbEnemyBrick = 222;

                    public static final char ssbTeamGoal = 223;
                    public static final char ssbEnemyGoal = 224;

                    public static final char ssbTeamFlag = 225;
                    public static final char ssbEnemyFlag = 226;

                    public static final char ssbPrize = 227;

                    public static final char ssbBorder = 228;           // Use ssbBorder instead of vieBorder to fill border



                     */
                    Vec3d location = new Vec3d(xpos, -ypos, 0).add(arenaOffset);
                    switch (s) {
                        case 170:
                            //Turf flag
                            ModuleGameEntities.createCaptureTheFlag(location, ed, time.getTime());
                            break;
                        case 216:
                            ModuleGameEntities.createOver1(location, ed, time.getTime());
                            break;
                        case 217:
                            //Medium asteroid
                            ModuleGameEntities.createOver2(location, ed, time.getTime());
                            break;
                        case 219:
                            //Station
                            break;
                        case 220:
                            ModuleGameEntities.createWormhole(location, 5, 5, 5000, GravityWell.PULL, new Vec3d(0, 0, 0), ed, time.getTime());
                            break;
                        default:
                            //ModuleGameEntities.createMapTile(map.m_file, s, location, TileTypes.LEGACY, ed, time.getTime());//
                            mapTileQueue.add(new MapTileCallable(map.m_file, s, location, TileTypes.LEGACY, ed, time.getTime()));
                            break;

                    }
                }
            }
        }
    }

    /**
     * Finds the map tile entity for the given coordinate
     *
     * @param coord the x,y-coordinate to lookup
     * @return the entityid of the map tile
     */
    public EntityId getEntityId(Vector2 coord) {
        return index.get(coord);
    }

    @Override
    protected void terminate() {
        //Release reader object
        reader = null;

        tileTypes.release();
        tileTypes = null;
    }

    @Override
    public void update(SimTime tpf) {

        this.time = tpf;

        //Create map:
        if (!mapCreated) {
            //createEntitiesFromLegacyMap(loadMap("Maps/tunnelbase.lvl"), new Vec3d(-MAP_SIZE*0.5, MAP_SIZE*0.25, 0));
            //createEntitiesFromLegacyMap(loadMap("Maps/tunnelbase.lvl"), new Vec3d(-MAP_SIZE, MAP_SIZE, 0));
            //createEntitiesFromLegacyMap(loadMap("Maps/trench.lvl"), new Vec3d(-HALF,HALF,0));
            //createEntitiesFromMap(loadMap("Maps/turretwarz.lvl"), new Vec3d(0,MAP_SIZE,0));
            mapCreated = true;
        }

        for (Vector2 remove : sessionTileRemovals) {
            Vector2 clampedLocation = getKey(remove);

            if (index.containsKey(clampedLocation)) {
                EntityId eId = index.get(clampedLocation);
                ed.removeEntity(eId);

                index.remove(remove);
            }
            //Update surrounding tiles
            ArrayList<Vector2> locations = new ArrayList<>();
            locations.add(clampedLocation);
            updateWangBlobIndexNumber(locations, true, false);
        }
        sessionTileRemovals.clear();

        for (Vector2 create : sessionTileCreations) {
            //Clamp location
            Vector2 clampedLocation = getKey(create);
            if (index.containsKey(clampedLocation)) {
                //A map entity already exists here
                continue;
            }

            ArrayList<Vector2> locations = new ArrayList<>();
            locations.add(clampedLocation);

            EntityId eId = ed.createEntity();

            index.put(clampedLocation, eId);

            short tileIndexNumber = updateWangBlobIndexNumber(locations, true, true);

            ModuleGameEntities.updateWangBlobEntity(eId, "", tileIndexNumber, new Vec3d(clampedLocation.x, clampedLocation.y, 0), ed, time.getTime());
        }
        sessionTileCreations.clear();

        try {
            if (mapTileQueue.size() > 2) {
                mapTileQueue.pop().call();
                mapTileQueue.pop().call();

            }
            if (mapTileQueue.size() > 0) {
                mapTileQueue.pop().call();

            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Updates the wang blob index number in the locations given
     *
     * @param locations the clamped locations to update wangblob tile index
     * number for
     * @param cascade cascade to surrounding tiles
     * @param create create or remove tile
     * @return the tileindex of the current tile being updated
     */
    private short updateWangBlobIndexNumber(ArrayList<Vector2> locations, boolean cascade, boolean create) {
        int result = 0;
        ArrayList<Vector2> cascadedLocations = new ArrayList<>();

        for (Vector2 clampedLocation : locations) {
            int north = 0, northEast = 0, east = 0, southEast = 0, south = 0, southWest = 0, west = 0, northWest = 0;

            north = 0;
            Vector2 northKey = clampedLocation.copy().add(0, 1);
            if (index.containsKey(northKey)) {
                north = 1;
                if (cascade) {
                    cascadedLocations.add(northKey);
                }
            }

            northEast = 0;
            Vector2 northEastKey = clampedLocation.copy().add(1, 1);
            if (index.containsKey(northEastKey)) {
                northEast = 1;
                if (cascade) {
                    cascadedLocations.add(northEastKey);
                }
            }

            east = 0;
            Vector2 eastKey = clampedLocation.copy().add(1, 0);
            if (index.containsKey(eastKey)) {
                east = 1;
                if (cascade) {
                    cascadedLocations.add(eastKey);
                }
            }

            southEast = 0;
            Vector2 southEastKey = clampedLocation.copy().add(1, -1);
            if (index.containsKey(southEastKey)) {
                southEast = 1;
                if (cascade) {
                    cascadedLocations.add(southEastKey);
                }
            }

            south = 0;
            Vector2 southKey = clampedLocation.copy().add(0, -1);
            if (index.containsKey(southKey)) {
                south = 1;
                if (cascade) {
                    cascadedLocations.add(southKey);
                }
            }

            southWest = 0;
            Vector2 southWestKey = clampedLocation.copy().add(-1, -1);
            if (index.containsKey(southWestKey)) {
                southWest = 1;
                if (cascade) {
                    cascadedLocations.add(southWestKey);
                }
            }

            west = 0;
            Vector2 westKey = clampedLocation.copy().add(-1, 0);
            if (index.containsKey(westKey)) {
                west = 1;
                if (cascade) {
                    cascadedLocations.add(westKey);
                }
            }

            northWest = 0;
            Vector2 northWestkey = clampedLocation.copy().add(-1, 1);
            if (index.containsKey(northWestkey)) {
                northWest = 1;
                if (cascade) {
                    cascadedLocations.add(northWestkey);
                }
            }

            if (north == 0) {
                northEast = 0;
                northWest = 0;
            }

            if (west == 0) {
                northWest = 0;
                southWest = 0;
            }

            if (south == 0) {
                southEast = 0;
                southWest = 0;
            }

            if (east == 0) {
                southEast = 0;
                northEast = 0;
            }

            result = north
                    + 2 * northEast
                    + 4 * east
                    + 8 * southEast
                    + 16 * south
                    + 32 * southWest
                    + 64 * west
                    + 128 * northWest;

            if (create || !cascade) {

                EntityId currentEntity = index.get(clampedLocation);
                TileType tt = TileTypes.wangblob("", (short) result, ed);

                ed.setComponent(currentEntity, tt);
            }

        }

        if (cascade && cascadedLocations.size() > 0) {
            updateWangBlobIndexNumber(cascadedLocations, false, create);
        }

        return (short) result;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }

    /**
     * Returns a tile location key based on a Vector2
     *
     * @param location the Vector2 to clamp
     * @return the clamped Vector2 location
     */
    private Vector2 getKey(Vector2 location) {
        Vector2 coordinates = new Vector2(Math.round(location.x - 0.5) + 0.5, Math.round(location.y - 0.5) + 0.5);
        return coordinates;
    }

    /**
     * Returns a tile location key based on a Vec3d
     *
     * @param location the Vec3d to clamp
     * @return the clamped Vector2 location
     */
    private Vector2 getKey(Vec3d location) {
        Vector2 coordinates = new Vector2(Math.round(location.x - 0.5) + 0.5, Math.round(location.y - 0.5) + 0.5);
        return coordinates;
    }

    /**
     * Queue up a tile for removal
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     */
    public void sessionRemoveTile(double x, double y) {
        Vector2 clampedLocation = getKey(new Vector2(x, y));
        sessionTileRemovals.add(clampedLocation);
    }

    /**
     * Queue up a tile for creation
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     */
    public void sessionCreateTile(double x, double y) {
        Vector2 clampedLocation = getKey(new Vector2(x, y));
        sessionTileCreations.add(clampedLocation);
    }

    private Grid createDungeonGrid() {
        Grid result = new Grid(100, 100);

        DungeonGenerator dungeonGenerator = new DungeonGenerator();

        dungeonGenerator.setCorridorThreshold(NOISE4J_CORRIDOR);
        dungeonGenerator.setFloorThreshold(NOISE4J_FLOOR);
        dungeonGenerator.setWallThreshold(NOISE4J_WALL);

        dungeonGenerator.setRoomGenerationAttempts(100);
        dungeonGenerator.setMaxRoomsAmount(10);
        dungeonGenerator.addRoomTypes(DefaultRoomType.values());

        //Max first, then min. Only odd values
        dungeonGenerator.setMaxRoomSize(21);
        dungeonGenerator.setMinRoomSize(9);

        dungeonGenerator.generate(result);

        //result = carveCorridors(result);
        return result;
    }

    /**
     * So I dont forget:
     *
     * Go through grid, carve all corridors to be 2 wide in a copy Assign copy
     * to grid Go through grid, set all tiles adjacent to floor or corridor to
     * wall Create one or more entrances
     *
     */
    /**
     * Expands the corridors of a (dungeon) Grid by one
     *
     * @param grid the Grid to expand corridors in
     * @return the new Grid with expanded corridors
     */
    private Grid expandCorridors(Grid grid) {
        Grid newGrid = grid.copy();

        for (int i = 0; i < grid.getWidth(); i++) {
            for (int j = 0; j < grid.getHeight(); j++) {
                if (grid.get(i, j) == NOISE4J_CORRIDOR) {
                    //newGrid.set(i - 1, j - 1, 0f);
                    //newGrid.set(i - 1, j, 0f);
                    //newGrid.set(i - 1, j + 1, 0f);
                    //newGrid.set(i + 1, j - 1, 0f);

                    if (grid.get(i + 1, j) == NOISE4J_WALL && grid.get(i + 1, j) != NOISE4J_FLOOR) {
                        newGrid.set(i + 1, j, NOISE4J_CORRIDOR);
                    }
                    if (grid.get(i + 1, j + 1) == NOISE4J_WALL && grid.get(i + 1, j + 1) != NOISE4J_FLOOR) {
                        newGrid.set(i + 1, j + 1, NOISE4J_CORRIDOR);
                    }
                    if (grid.get(i, j + 1) == NOISE4J_WALL && grid.get(i, j + 1) != NOISE4J_FLOOR) {
                        newGrid.set(i, j + 1, NOISE4J_CORRIDOR);
                    }
                    //newGrid.set(i, j - 1, 0f);
                    //newGrid.set(i, j, 0f);
                }
            }
        }
        grid.set(newGrid);

        return grid;
    }

    /**
     * Creates map tiles from a Dungeon Grid. Default values: wallThreshold =
     * 1f; floorThreshold = 0.5f; corridorThreshold = 0f; Use the statics
     * NOISE4J_*
     *
     * @param grid the Grid to create entities from
     * @param offsetX the offset x-coordinate to create the entities in
     * @param offsetY the offset y-coordinate to create the entities in
     */
    private void createMapTilesFromDungeonGrid(Grid grid, float offsetX, float offsetY) {
        /*

         */
        float f;
        for (int i = 0; i < grid.getHeight(); i++) {
            for (int j = 0; j < grid.getWidth(); j++) {
                f = grid.get(j, i);
                if (f == 0f || f == 0.5f) {
                    //Floors (rooms) && Corridors

                    //Should create maptiles around rooms and corridors
                    if (grid.get(j + 1, i) == 1f) {
                        sessionCreateTile(j + 1 + offsetX, i + offsetY);
                    }
                    if (grid.get(j - 1, i) == 1f) {
                        sessionCreateTile(j - 1 + offsetX, i + offsetY);
                    }
                    if (grid.get(j, i + 1) == 1f) {
                        sessionCreateTile(j + offsetX, i + 1 + offsetY);
                    }
                    if (grid.get(j, i - 1) == 1f) {
                        sessionCreateTile(j + offsetX, i - 1 + offsetY);
                    }
                    if (grid.get(j + 1, i + 1) == 1f) {
                        sessionCreateTile(j + 1 + offsetX, i + 1 + offsetY);
                    }
                    if (grid.get(j - 1, i + 1) == 1f) {
                        sessionCreateTile(j - 1 + offsetX, i + 1 + offsetY);
                    }
                    if (grid.get(j + 1, i - 1) == 1f) {
                        sessionCreateTile(j + 1 + offsetX, i - 1 + offsetY);
                    }
                    if (grid.get(j - 1, i - 1) == 1f) {
                        sessionCreateTile(j - 1 + offsetX, i - 1 + offsetY);
                    }
                }
            }
        }
    }

    class MapTileCallable implements Callable<EntityId> {

        String m_file;
        short s;
        Vec3d loc;
        String type;
        EntityData ed;
        long time;

        public MapTileCallable(String m_file, short s, Vec3d location, String type, EntityData ed, long time) {
            this.m_file = m_file;
            this.s = s;
            this.loc = location;
            this.type = type;
            this.ed = ed;
            this.time = time;
        }

        @Override
        public EntityId call() throws Exception {
            EntityId id = ModuleGameEntities.createMapTile(m_file, s, loc, type, ed, time);
            log.debug("Called up creation of entity: "+id+". "+this.toString());
            return id;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("MapTileCallable{m_file=").append(m_file);
            sb.append(", s=").append(s);
            sb.append(", loc=").append(loc);
            sb.append(", type=").append(type);
            sb.append(", ed=").append(ed);
            sb.append(", time=").append(time);
            sb.append('}');
            return sb.toString();
        }
    }
}
