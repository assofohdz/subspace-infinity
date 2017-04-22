package example.es.states;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
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
    @Override
    protected void initialize() {
        // Arrange
        reader = new TMXMapReader();
        // Assert
        assertTrue(reader.accept(new File("resources/sewers.tmx")));
        
        try {
            testReadingExampleMap();
        } catch (Exception ex) {
            Logger.getLogger(MapState.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    protected void terminate() {
        //Release reader object
        reader = null;
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
