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
package infinity.client;

import infinity.client.CameraState;
import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResults;
import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.jme3.texture.Image;
import com.jme3.texture.plugins.AWTLoader;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.AndFilter;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import infinity.ConnectionState;
import infinity.api.es.Position;
import infinity.api.es.TileType;
import infinity.api.es.TileTypes;
import infinity.api.es.ViewType;
import infinity.api.es.ViewTypes;
import infinity.map.LevelFile;
import infinity.map.LevelLoader;
import infinity.net.GameSession;
import infinity.net.client.GameSessionClientService;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State
 *
 * @author Asser
 */
public class MapStateClient extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(MapStateClient.class);
    
    AndFilter andFilter;
    private EntityData ed;
    private LegacyMapImageContainer tileImages;
    private java.util.Map<Vector2, EntityId> index = new ConcurrentHashMap<>();
    private AssetManager am;
    private HashMap<String, LevelFile> levelFiles = new HashMap<>();
    private AWTLoader imgLoader;
    private HashMap<TileKey, Image> imageMap = new HashMap<>();
    private FieldFilter legacyFilter;
    private FieldFilter wangBlobFilter;
    private EntitySet arenas;

    private HashMap<Integer, WangInfo> wangBlobIndexMap = new HashMap<>();

    @Override
    protected void initialize(Application app) {

        this.ed = getState(ConnectionState.class).getEntityData();

        this.am = app.getAssetManager();

        am.registerLoader(LevelLoader.class, "lvl");

        imgLoader = new AWTLoader();

        this.generateWangBlobInfoMap(wangBlobIndexMap);

        legacyFilter = FieldFilter.create(TileType.class, "type", ed.getStrings().getStringId(TileTypes.LEGACY, false));
        wangBlobFilter = FieldFilter.create(TileType.class, "type", ed.getStrings().getStringId(TileTypes.WANGBLOB, false));

        arenas = ed.getEntities(FieldFilter.create(ViewType.class, "type", ViewTypes.ARENA), ViewType.class, Position.class);
    }

    public float getWangBlobRotations(int indexNumber) {
        if (!wangBlobIndexMap.containsKey(indexNumber)) {
            throw new NullPointerException("WangBlobMap does not contain index number: " + indexNumber);
        }
        return wangBlobIndexMap.get(indexNumber).getRotation();
    }

    public int getWangBlobTileNumber(int indexNumber) {
        if (!wangBlobIndexMap.containsKey(indexNumber)) {
            throw new NullPointerException("WangBlobMap does not contain index number: " + indexNumber);
        }
        return wangBlobIndexMap.get(indexNumber).getTileNumber();
    }

    private void generateWangBlobInfoMap(HashMap<Integer, WangInfo> map) {
        //Zero
        map.put(0, new WangInfo(0, 0));
        //One
        map.put(1, new WangInfo(1, 0));
        map.put(4, new WangInfo(1, 1));
        map.put(16, new WangInfo(1, 2));
        map.put(64, new WangInfo(1, 3));
        //Five
        map.put(5, new WangInfo(2, 0));
        map.put(20, new WangInfo(2, 1));
        map.put(80, new WangInfo(2, 2));
        map.put(65, new WangInfo(2, 3));
        //Seven
        map.put(7, new WangInfo(3, 0));
        map.put(28, new WangInfo(3, 1));
        map.put(112, new WangInfo(3, 2));
        map.put(193, new WangInfo(3, 3));
        //Seventeen         
        map.put(17, new WangInfo(4, 0));
        map.put(68, new WangInfo(4, 1));
        //Twentyone
        map.put(21, new WangInfo(5, 0));
        map.put(84, new WangInfo(5, 1));
        map.put(81, new WangInfo(5, 2));
        map.put(69, new WangInfo(5, 3));
        //Twentythree
        map.put(23, new WangInfo(6, 0));
        map.put(92, new WangInfo(6, 1));
        map.put(113, new WangInfo(6, 2));
        map.put(197, new WangInfo(6, 3));
        //TwentyNine
        map.put(29, new WangInfo(7, 0));
        map.put(116, new WangInfo(7, 1));
        map.put(209, new WangInfo(7, 2));
        map.put(71, new WangInfo(7, 3));
        //ThirtyOne
        map.put(31, new WangInfo(8, 0));
        map.put(124, new WangInfo(8, 1));
        map.put(241, new WangInfo(8, 2));
        map.put(199, new WangInfo(8, 3));
        //EightFive
        map.put(85, new WangInfo(9, 0));
        //EightySeven
        map.put(87, new WangInfo(10, 0));
        map.put(93, new WangInfo(10, 1));
        map.put(117, new WangInfo(10, 2));
        map.put(213, new WangInfo(10, 3));
        //NinetyFive
        map.put(95, new WangInfo(11, 0));
        map.put(125, new WangInfo(11, 1));
        map.put(245, new WangInfo(11, 2));
        map.put(215, new WangInfo(11, 3));
        //OneHundredAndNineTeen
        map.put(119, new WangInfo(12, 0));
        map.put(221, new WangInfo(12, 1));
        //OneHundredAndTwentySeven
        map.put(127, new WangInfo(13, 0));
        map.put(253, new WangInfo(13, 1));
        map.put(247, new WangInfo(13, 2));
        map.put(223, new WangInfo(13, 3));
        //TwoHundredAndFiftyFive
        map.put(255, new WangInfo(14, 0));
        //Second row
    }

    public Image getImage(EntityId entityId) {
        return tileImages.getObject(entityId);
    }

    protected LevelFile loadMap(String tileSet) {

        LevelFile localMap = (LevelFile) am.loadAsset(tileSet);

        return localMap;
    }

    public EntityId getEntityId(Vector2 coord) {
        return index.get(coord);
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        //tileImages = new LegacyMapImageContainer(ed);
        //tileImages.start();
    }

    @Override
    protected void onDisable() {
        //tileImages.stop();
        //tileImages = null;
    }

    @Override
    public void update(float tpf) {
    }

    /**
     * Converts a given Image into a BufferedImage
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    private BufferedImage toBufferedImage(java.awt.Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        int width = img.getWidth(null);
        int height = img.getHeight(null);

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);                          //No flip
        //bGr.drawImage(img, 0 + width, 0, -width, height, null);  //Horisontal flip
        //bGr.drawImage(img, 0, 0 + height, width, -height, null);   //Vertical flip
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }

    //Map the entities to their texture
    private class LegacyMapImageContainer extends EntityContainer<Image> {

        public LegacyMapImageContainer(EntityData ed) {
            super(ed, legacyFilter, TileType.class, Position.class);
        }

        @Override
        protected Image[] getArray() {
            return super.getArray();
        }

        @Override
        protected Image addObject(Entity e) {
            TileType ti = e.get(TileType.class);
            String tileSet = ti.getTileSet();
            short tileIndex = ti.getTileIndex();
            TileKey key = new TileKey(tileSet, tileIndex);

            if (!imageMap.containsKey(key)) {

                if (!levelFiles.containsKey(tileSet)) {
                    LevelFile lf = loadMap(tileSet); //TODO: Should be done in a non-intrusive way
                    levelFiles.put(tileSet, lf);

                }

                java.awt.Image awtInputImage = levelFiles.get(tileSet).getTiles()[tileIndex - 1];
                Image jmeOutputImage = imgLoader.load(toBufferedImage(awtInputImage), true);

                imageMap.put(key, jmeOutputImage);

            }

            return imageMap.get(key);
        }

        @Override
        protected void updateObject(Image object, Entity e) {
            //Does not support mass updating tiles right now
            //TODO: The tileIndex in a TileInfo component could change
        }

        @Override
        protected void removeObject(Image object, Entity e) {
            //We leave the levels loaded
        }
    }

    public class TileKey {

        private final String tileSet;
        private final short tileIndex;

        public TileKey(String x, short y) {
            this.tileSet = x;
            this.tileIndex = y;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.tileSet);
            hash = 97 * hash + this.tileIndex;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TileKey other = (TileKey) obj;
            if (this.tileIndex != other.tileIndex) {
                return false;
            }
            if (!Objects.equals(this.tileSet, other.tileSet)) {
                return false;
            }
            return true;
        }

    }

    private class WangInfo {

        private final int tileNumber;
        private final float radianRotation;

        public WangInfo(int tileNumber, float rotation) {
            this.tileNumber = tileNumber;
            this.radianRotation = rotation;
        }

        public int getTileNumber() {
            return tileNumber;
        }

        public float getRotation() {
            return radianRotation;
        }
    }

    public void setMapEditingActive(boolean active) {

    }

    public void addArenaMouseListeners(Spatial arena) {

        MouseEventControl.addListenersToSpatial(arena,
                new DefaultMouseListener() {

            boolean isPressed;
            int keyIndex;

            private int xDown;
            private int yDown;

            @Override
            protected void click(MouseButtonEvent event, Spatial target, Spatial capture) {

            }

            @Override
            public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture) {
                event.setConsumed();

                isPressed = event.isPressed();
                keyIndex = event.getButtonIndex();

                if (event.isPressed()) {
                    xDown = event.getX();
                    yDown = event.getY();
                } else if (isClick(event, xDown, yDown)) {
                    click(event, target, capture);
                }
            }

            @Override
            public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
                //Material m = ((Geometry) target).getMaterial();
                //m.setColor("Color", ColorRGBA.Yellow);
            }

            @Override
            public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
                //Material m = ((Geometry) target).getMaterial();
                //m.setColor("Color", ColorRGBA.Blue);
            }

            @Override
            public void mouseMoved(MouseMotionEvent event, Spatial target, Spatial capture) {
                if (isPressed && keyIndex == MouseInput.BUTTON_LEFT) {
                    GameSession session = getState(ConnectionState.class).getService(GameSessionClientService.class);
                    if (session == null) {
                        throw new RuntimeException("ModelViewState requires an active game session.");
                    }
                    Camera cam = getState(CameraState.class).getCamera();

                    Vector2f click2d = new Vector2f(event.getX(), event.getY());

                    Vector3f click3d = cam.getWorldCoordinates(click2d.clone(), 0f).clone();
                    Vector3f dir = cam.getWorldCoordinates(click2d.clone(), 1f).subtractLocal(click3d).normalizeLocal();

                    Ray ray = new Ray(click3d, dir);
                    CollisionResults results = new CollisionResults();
                    target.collideWith(ray, results);
                    if (results.size() != 1) {
                        log.error("There should only be one collision with the arena when the user clicks it");
                    }
                    Vector3f contactPoint = results.getCollision(0).getContactPoint();
                    session.createTile("", contactPoint.x, contactPoint.y);
                }

                if (isPressed && keyIndex == MouseInput.BUTTON_RIGHT) {
                    GameSession session = getState(ConnectionState.class).getService(GameSessionClientService.class);
                    if (session == null) {
                        throw new RuntimeException("ModelViewState requires an active game session.");
                    }
                    Camera cam = getState(CameraState.class).getCamera();

                    Vector2f click2d = new Vector2f(event.getX(), event.getY());

                    Vector3f click3d = cam.getWorldCoordinates(click2d.clone(), 0f).clone();
                    Vector3f dir = cam.getWorldCoordinates(click2d.clone(), 1f).subtractLocal(click3d).normalizeLocal();

                    Ray ray = new Ray(click3d, dir);
                    CollisionResults results = new CollisionResults();
                    target.collideWith(ray, results);
                    if (results.size() != 1) {
                        log.error("There should only be one collision with the arena when the user clicks it");
                    }
                    Vector3f contactPoint = results.getCollision(0).getContactPoint();
                    session.removeTile(contactPoint.x, contactPoint.y);
                }
            }
        });
    }
}
