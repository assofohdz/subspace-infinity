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
import example.map.BitMapLoader;
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

        am.registerLoader(BitMapLoader.class, "bmp");
        am.registerLoader(LevelLoader.class, "lvl");

        String mapFile = "Maps/twbd.lvl";

        createEntitiesFromMap(loadMap(mapFile));
    }

    public LevelFile loadMap(String mapFile) {
        LevelFile map = (LevelFile) am.loadAsset(mapFile);
        return map;
    }

    public void createEntitiesFromMap(LevelFile map) {
        short[][] tiles = map.getMap();

        for (int i = 0; i < tiles.length; i++) {
            for (int j = 0; j < tiles[i].length; j++) {
                short s = tiles[i][j];
                if (s != 0) {
                    //TODO: Check on the short and only create the map tiles, not the extras (asteroids, wormholes etc.)
                    Vec3d location = new Vec3d(i, j, 0);
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
                            GameEntities.createWormhole(location, s, new Vec3d(0,0,0), s, s, GravityWell.PULL, location, ed);
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
