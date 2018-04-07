package example.es.states;

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
import java.util.ArrayList;
import java.util.HashSet;
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
    private Vector2 northKey, eastKey, westKey, southKey;
    private Vector2 northEastKey;
    private Vector2 southEastKey;
    private Vector2 southWestKey;
    private Vector2 northWestkey;

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
        tileTypes.applyChanges();
        if (tileTypes.hasChanges()) {
            for (Entity e : tileTypes.getAddedEntities()) {
                TileType tt = e.get(TileType.class);
                Position p = e.get(Position.class);
                Vec3d location = p.getLocation();
                //Clamp location
                Vector2 clampedLocation = getKey(location);
                location.x = clampedLocation.x;
                location.y = clampedLocation.y;

                index.put(clampedLocation, e.getId());

                ArrayList<Vector2> locations = new ArrayList<>();
                locations.add(clampedLocation);

                short tileIndexNumber = updateWangBlobIndexNumber(locations, true);

                CoreGameEntities.updateWangBlobEntity(e.getId(), tt.getTileSet(), tileIndexNumber, new Vec3d(clampedLocation.x, clampedLocation.y, 0), new Rectangle(1, 1), ed);
            }
        }
    }

    private short updateWangBlobIndexNumber(ArrayList<Vector2> locations, boolean cascade) {
        int result = 0;
        ArrayList<Vector2> cascadedLocations = new ArrayList<>();

        int north = 0, northEast = 0, east = 0, southEast = 0, south = 0, southWest = 0, west = 0, northWest = 0;

        for (Vector2 clampedLocation : locations) {
            northKey = clampedLocation.copy().add(0, 1);
            if (index.containsKey(northKey)) {
                north = 1;
                if (cascade) {
                    cascadedLocations.add(northKey);
                }
            }

            northEastKey = clampedLocation.copy().add(1, 1);
            if (index.containsKey(northEastKey)) {
                northEast = 1;
                if (cascade) {
                    cascadedLocations.add(northEastKey);
                }
            }

            eastKey = clampedLocation.copy().add(1, 0);
            if (index.containsKey(eastKey)) {
                east = 1;
                if (cascade) {
                    cascadedLocations.add(eastKey);
                }
            }

            southEastKey = clampedLocation.copy().add(1, -1);
            if (index.containsKey(southEastKey)) {
                southEast = 1;
                if (cascade) {
                    cascadedLocations.add(southEastKey);
                }
            }

            southKey = clampedLocation.copy().add(0, -1);
            if (index.containsKey(southKey)) {
                south = 1;
                if (cascade) {
                    cascadedLocations.add(southKey);
                }
            }

            southWestKey = clampedLocation.copy().add(-1, -1);
            if (index.containsKey(southWestKey)) {
                southWest = 1;
                if (cascade) {
                    cascadedLocations.add(southWestKey);
                }
            }

            westKey = clampedLocation.copy().add(-1, 0);
            if (index.containsKey(westKey)) {
                west = 1;
                if (cascade) {
                    cascadedLocations.add(westKey);
                }
            }

            northWestkey = clampedLocation.copy().add(-1, 1);
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

            result = north + 2 * northEast + 4 * east + 8 * southEast + 16 * south + 32 * southWest + 64 * west + 128 * northWest;

            EntityId currentEntity = index.get(clampedLocation);
            TileType tt = ed.getComponent(currentEntity, TileType.class);
            ed.setComponent(currentEntity, tt.newTileIndex((short) result, ed));
        }

        if (cascade && cascadedLocations.size() > 0) {
            updateWangBlobIndexNumber(cascadedLocations, false);
        }

        return (short) result;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    private Vector2 getKey(Vec3d location) {
        Vector2 coordinates = new Vector2(Math.round(location.x - 0.5) + 0.5, Math.round(location.y - 0.5) + 0.5);
        return coordinates;
    }
}
