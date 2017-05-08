package example.es.states;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.ArenaId;
import example.es.BodyPosition;
import example.es.HitPoints;
import example.sim.GameEntities;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.TestCase.assertEquals;
import tiled.io.TMXMapReader;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import tiled.core.Map;
import tiled.core.TileLayer;

/**
 *
 * @author Asser
 */
public class MapState extends AbstractGameSystem {

    private TMXMapReader reader;
    private Map map;
    private EntityData ed;
    private EntitySet entities;
    
    
    
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
        
        
        entities = ed.getEntities(ArenaId.class); //This filters all entities that are in arenas
        
        //TODO: Handle all arenas in a managed list
        EntityId arenaId = GameEntities.createArena(0, ed); //Create first arena
    }

    @Override
    protected void terminate() {
        //Release reader object
        reader = null;
        // Release the entity set we grabbed previously
        entities.release();
        entities = null;
    }
    
    @Override
    public void update(SimTime tpf) {
        
    }
    
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
}
