package example.es.states;

import com.github.czyzby.noise4j.map.Grid;
import com.github.czyzby.noise4j.map.generator.room.RoomType.DefaultRoomType;
import com.github.czyzby.noise4j.map.generator.room.dungeon.DungeonGenerator;
import com.jme3.asset.AssetManager;
import com.jme3.system.JmeSystem;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.AndFilter;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.GameConstants;
import example.es.GravityWell;
import example.es.Position;
import example.es.TileType;
import example.es.TileTypes;
import example.es.WarpTo;
import example.map.LevelFile;
import example.map.LevelLoader;
import example.sim.CoreGameEntities;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import org.dyn4j.geometry.Convex;
import tiled.io.TMXMapReader;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;

/**
 * State
 *
 * @author Asser
 */
public class MapStateServer extends AbstractGameSystem {

    private TMXMapReader reader;
    private EntityData ed;
    private java.util.Map<Vector2, EntityId> index = new ConcurrentHashMap<>();
    private AssetManager am;
    public static final int MAP_SIZE = 1024;
    private static final int HALF = 512;
    private EntitySet tileTypes;

    private LinkedHashSet<Vector2> sessionTileRemovals = new LinkedHashSet<>();
    private LinkedHashSet<Vector2> sessionTileCreations = new LinkedHashSet<>();

    public static final float NOISE4J_CORRIDOR = 0f;
    public static final float NOISE4J_FLOOR = 0.5f;
    public static final float NOISE4J_WALL = 1f;

    //int[][] multD = new int[5][];
    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);

        am = JmeSystem.newAssetManager(Thread.currentThread().getContextClassLoader().getResource("com/jme3/asset/Desktop.cfg"));

        am.registerLoader(LevelLoader.class, "lvl");

        tileTypes = ed.getEntities(TileType.class, Position.class);

        //
        //createEntitiesFromMap(loadMap("Maps/tunnelbase.lvl"), new Vec3d(0,0,0));
        //createEntitiesFromMap(loadMap("Maps/extreme.lvl"), new Vec3d(-MAP_SIZE,0,0));
        //createEntitiesFromMap(loadMap("Maps/tunnelbase.lvl"), new Vec3d(-MAP_SIZE, MAP_SIZE, 0));
        //createEntitiesFromMap(loadMap("Maps/turretwarz.lvl"), new Vec3d(0,MAP_SIZE,0));
        Grid dungeon = this.createDungeonGrid();
        dungeon = this.expandCorridors(dungeon);
        this.createMapTilesFromDungeonGrid(dungeon, -50f, -50f);

    }

    public Vector2 getCenterOfArena(double currentXCoord, double currentYCoord) {
        double xArenaCoord = Math.floor(currentXCoord / MAP_SIZE);
        double yArenaCoord = Math.floor(currentYCoord / MAP_SIZE);

        double centerOfArenaX = currentXCoord < 0 ? xArenaCoord - HALF : xArenaCoord + HALF;
        double centerOfArenaY = currentYCoord < 0 ? yArenaCoord - HALF : yArenaCoord + HALF;

        return new Vector2(centerOfArenaX, centerOfArenaY);
    }

    public LevelFile loadMap(String mapFile) {
        LevelFile map = (LevelFile) am.loadAsset(mapFile);
        return map;
    }

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
                            CoreGameEntities.createCaptureTheFlag(location, ed);
                            break;
                        case 216:
                            CoreGameEntities.createOver1(location, ed);
                            break;
                        case 217:
                            //Medium asteroid
                            CoreGameEntities.createOver2(location, ed);
                            break;
                        case 219:
                            //Station
                            break;
                        case 220:
                            CoreGameEntities.createWormhole(location, 5, 5, 5000, GravityWell.PULL, new Vec3d(0, 0, 0), ed);
                            break;
                        default:
                            CoreGameEntities.createMapTile(map.m_file, s, location, new Rectangle(1, 1), TileTypes.LEGACY, ed);//
                            break;
                    }
                }
            }
        }
    }

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

            CoreGameEntities.updateWangBlobEntity(eId, "", tileIndexNumber, new Vec3d(clampedLocation.x, clampedLocation.y, 0), new Rectangle(1, 1), ed);
        }
        sessionTileCreations.clear();

        /*
        tileTypes.applyChanges();
        if (tileTypes.hasChanges()) {
            for (Entity e : tileTypes.getAddedEntities()) {

                TileType tt = e.get(TileType.class);
                Position p = e.get(Position.class);
                Vec3d location = p.getLocation();
                //Clamp location
                Vector2 clampedLocation = getKey(location);
                if (index.containsKey(clampedLocation)) {
                    //A map entity already exists here
                    ed.removeEntity(e.getId());
                    continue;
                }

                index.put(clampedLocation, e.getId());

                ArrayList<Vector2> locations = new ArrayList<>();
                locations.add(clampedLocation);

                short tileIndexNumber = updateWangBlobIndexNumber(locations, true);

                CoreGameEntities.updateWangBlobEntity(e.getId(), tt.getTileSet(), tileIndexNumber, new Vec3d(clampedLocation.x, clampedLocation.y, 0), new Rectangle(1, 1), ed);
            }
        }*/
    }

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

    private Vector2 getKey(Vector2 location) {
        Vector2 coordinates = new Vector2(Math.round(location.x - 0.5) + 0.5, Math.round(location.y - 0.5) + 0.5);
        return coordinates;
    }

    private Vector2 getKey(Vec3d location) {
        Vector2 coordinates = new Vector2(Math.round(location.x - 0.5) + 0.5, Math.round(location.y - 0.5) + 0.5);
        return coordinates;
    }

    public void sessionRemoveTile(double x, double y) {
        Vector2 clampedLocation = getKey(new Vector2(x,y));
        sessionTileRemovals.add(clampedLocation);
    }

    public void sessionCreateTile(double x, double y) {
        Vector2 clampedLocation = getKey(new Vector2(x,y));
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

    private void createMapTilesFromDungeonGrid(Grid grid, float offsetX, float offsetY) {
        /*
        Default values:
        private float wallThreshold = 1f;
        private float floorThreshold = 0.5f;
        private float corridorThreshold = 0f;
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
}
