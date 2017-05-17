package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.ArenaId;
import example.es.MapTileType;
import example.es.MapTileTypes;
import example.es.Position;
import example.sim.GameEntities;
import java.util.concurrent.ConcurrentHashMap;
import tiled.io.TMXMapReader;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import tiled.core.Map;

/**
 * State
 *
 * @author Asser
 */
public class MapState extends AbstractGameSystem {

    private TMXMapReader reader;
    private Map map;
    private EntityData ed;
    private EntitySet arenaEntities;
    private BodyContainer mapTiles;
    private java.util.Map<Vector2, EntityId> index = new ConcurrentHashMap<>();

    @Override
    protected void initialize() {
        /*
        // Arrange
        reader = new TMXMapReader();
        // Assert
        assertTrue(reader.accept(new File("resources/sewers.tmx")));
        
        try {
            testReadingExampleMap();
        } catch (Exception ex) {
            Logger.getLogger(MapState.class.getName()).log(Level.SEVERE, null, ex);
        }
         */
        this.ed = getSystem(EntityData.class);

        arenaEntities = ed.getEntities(ArenaId.class); //This filters all entities that are in arenas


        //TODO: Handle all arenas in a managed list
        EntityId arenaId = GameEntities.createArena(0, ed); //Create first arena
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
        // Release the entity set we grabbed previously
        arenaEntities.release();
        arenaEntities = null;
    }

    @Override
    public void update(SimTime tpf) {
        mapTiles.update();
    }

    @Override
    public void start() {
        mapTiles = new MapState.BodyContainer(ed);
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

    /*
    private File getFileFromResources(String filename) throws URISyntaxException {
        // Need to load files with their absolute paths, since they might refer to
        // tileset files that are expected to be in the same directory as the TMX file.
        URL fileUrl = this.getClass().getResource(filename);
        assertNotNull(fileUrl);
        File mapFile = new File(fileUrl.toURI());
        assertTrue(mapFile.exists());
        return mapFile;
    }
    
    public void testReadingExampleMap() throws Exception {
        // Arrange
        File mapFile = getFileFromResources("resources/sewers.tmx");

        // Act
        map = new TMXMapReader().readMap(mapFile.getAbsolutePath());

        // Assert
        assertEquals(Map.ORIENTATION_ORTHOGONAL, map.getOrientation());
        assertEquals(50, map.getHeight());
        assertEquals(50, map.getHeight());
        assertEquals(24, map.getTileWidth());
        assertEquals(24, map.getTileHeight());
        assertEquals(3, map.getLayerCount());
        assertNotNull(((TileLayer)map.getLayer(0)).getTileAt(0, 0));
    }
     */
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
