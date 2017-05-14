package example.es.states;

import com.simsilica.es.Entity;
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
import tiled.io.TMXMapReader;
import org.dyn4j.geometry.Rectangle;
import tiled.core.Map;

/**
 * State 
 * @author Asser
 */
public class MapState extends AbstractGameSystem {

    private TMXMapReader reader;
    private Map map;
    private EntityData ed;
    private EntitySet arenaEntities, mapTileEntities;
    
    
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
        
        mapTileEntities = ed.getEntities(MapTileType.class, Position.class); //Do not really care about the physics here (SimplePhysics handles that)
        
        
        //TODO: Handle all arenas in a managed list
        EntityId arenaId = GameEntities.createArena(0, ed); //Create first arena
    }

    @Override
    protected void terminate() {
        //Release reader object
        reader = null;
        // Release the entity set we grabbed previously
        arenaEntities.release();
        arenaEntities = null;
        
        
        mapTileEntities.release();
        mapTileEntities = null;
    }
    
    @Override
    public void update(SimTime tpf) {
        mapTileEntities.applyChanges();
        
        for (Entity e : mapTileEntities.getAddedEntities()) {
            
            
            //TODO: Check if e is next to a removed entity as well
        }
        
        for (Entity e : mapTileEntities.getRemovedEntities()) {
            
            
            
            //TODO: Check if e is next to an added entity as well
        }
    }
    
    public void editMap(double x, double y){
        
        //TODO: Different shapes and different types
        GameEntities.createMapTile(MapTileTypes.tile(ed), new Vec3d(Math.round(x-0.5)+0.5, Math.round(y-0.5)+0.5, 0), ed, new Rectangle(1,1)); //TODO: Account for actual arena (z-pos)
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
}
