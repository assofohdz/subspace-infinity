package example.es.states;

import com.jme3.asset.AssetManager;
import com.jme3.system.JmeSystem;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.ArenaId;
import example.es.GravityWell;
import example.es.MapTileType;
import example.es.MapTileTypes;
import example.es.Position;
import example.es.TileInfo;
import example.map.LevelFile;
import example.map.LevelLoader;
import example.sim.GameEntities;
import java.util.concurrent.ConcurrentHashMap;
import org.dyn4j.geometry.Convex;
import tiled.io.TMXMapReader;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import tiled.core.Map;

/**
 * State
 *
 * @author Asser
 */
public class ServerMapState extends AbstractGameSystem {

    private TMXMapReader reader;
    private EntityData ed;
    private BodyContainer mapTiles;
    private java.util.Map<Vector2, EntityId> index = new ConcurrentHashMap<>();
    private AssetManager am;

    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);

        am = JmeSystem.newAssetManager(Thread.currentThread().getContextClassLoader().getResource("com/jme3/asset/Desktop.cfg"));

        am.registerLoader(LevelLoader.class, "lvl");

        String mapFile = "Maps/twbd.lvl";

        createEntitiesFromMap(loadMap(mapFile), 0);
    }

    public LevelFile loadMap(String mapFile) {
        LevelFile map = (LevelFile) am.loadAsset(mapFile);
        return map;
    }

    public void createEntitiesFromMap(LevelFile map, int arenaOffset) {
        short[][] tiles = map.getMap();

        for (int xpos = 0; xpos < tiles.length; xpos++) {
            for (int ypos = tiles[xpos].length - 1; ypos >= 0; ypos--) {
                short s = tiles[xpos][ypos];
                if (s != 0) {
                    //TODO: Check on the short and only create the map tiles, not the extras (asteroids, wormholes etc.)
                    /*
                    Row 2, tile 1 - Border tile
                    Row 9, tile 10 - Vertical warpgate (Mostly open)
                    Row 9, tile 11 - Vertical warpgate (Frequently open)
                    Row 9, tile 12 - Vertical warpgate (Frequently closed)
                    Row 9, tile 13 - Vertical warpgate (Mostly closed)
                    Row 9, tile 14 - Horizontal warpgate (Mostly open)
                    Row 9, tile 15 - Horizontal warpgate (Frequently open)
                    Row 9, tile 16 - Horizontal warpgate (Frequently closed)
                    Row 9, tile 17 - Horizontal warpgate (Mostly closed)
                    Row 9, tile 18 - Flag for turf
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
                     */
                    Vec3d location = new Vec3d(xpos, -ypos, 0);
                    switch (s) {
                        case 216:
                            GameEntities.createOver1(location, ed);
                            break;
                        case 217:
                            //Medium asteroid
                            break;
                        case 219:
                            //Station
                            break;
                        case 220:
                            GameEntities.createWormhole(location, s, new Vec3d(0, 0, 0), s, s, GravityWell.PULL, location, ed);
                            break;
                        default:
                            GameEntities.createTileInfo(map.m_file, s, location, new Rectangle(1, 1), 0.0, ed);//
                            break;
                    }
                }
            }
        }
    }

    public EntityId getEntityId(Vector2 coord) {
        return index.get(coord);
    }

    protected boolean removeMapTileCoord(Vector2 vector2) {
        EntityId result = index.remove(vector2);
        if (result != null) {
            return true;
        }
        return false;
    }

    @Override
    protected void terminate() {
        //Release reader object
        reader = null;
    }

    @Override
    public void update(SimTime tpf) {
        mapTiles.update();
    }

    @Override
    public void start() {
        mapTiles = new ServerMapState.BodyContainer(ed);
        mapTiles.start();
    }

    @Override
    public void stop() {
        mapTiles.stop();
        mapTiles = null;
    }

    public void editMap(double x, double y) {
        Vector2 coordinates = new Vector2(Math.round(x - 0.5) + 0.5, Math.round(y - 0.5) + 0.5);

        if (index.containsKey(coordinates)) {
            //TODO: There's already a tile there, attempt to change it or remove it

            //For now, just remove it
            ed.removeEntity(index.get(coordinates));

        } else {
            GameEntities.createMapTile(MapTileTypes.solid(ed), new Vec3d(coordinates.x, coordinates.y, 0), ed, new Rectangle(1, 1)); //TODO: Account for actual arena (z-pos)
        }
    }

    private class BodyContainer extends EntityContainer<Vector2> {

        public BodyContainer(EntityData ed) {
            super(ed, Position.class, MapTileType.class);
        }

        @Override
        protected Vector2[] getArray() {
            return super.getArray();
        }

        @Override
        protected Vector2 addObject(Entity e) {
            Position pos = e.get(Position.class);
            Vector2 result = new Vector2(pos.getLocation().x, pos.getLocation().y);

            index.put(result, e.getId());

            return result;
        }

        @Override
        protected void updateObject(Vector2 object, Entity e) {
            //Does not support mass updating tiles right now
            //TODO: Perhaps MapTileType could switch
        }

        @Override
        protected void removeObject(Vector2 object, Entity e) {
            removeMapTileCoord(object);
        }
    }
}
