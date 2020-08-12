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
package infinity.client.view;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.simsilica.es.filter.AndFilter;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.simsilica.mathd.Vec3d;

import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;
import infinity.es.BodyPosition;
import infinity.es.TileType;
import infinity.map.LevelFile;
import infinity.map.LevelLoader;
import infinity.net.GameSession;
import infinity.systems.MapSystem;

/**
 * State
 *
 * @author Asser
 */
public class MapState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(MapState.class);

    AndFilter andFilter;
    private EntityData ed;
    private LegacyMapImageContainer tileImages;
    private final java.util.Map<Vec3d, EntityId> index = new ConcurrentHashMap<>();
    private AssetManager am;
    private final HashMap<String, LevelFile> levelFiles = new HashMap<>();
    private AWTLoader imgLoader;
    private final HashMap<TileKey, Image> imageMap = new HashMap<>();

    private final HashMap<Integer, WangInfo> wangBlobIndexMap = new HashMap<>();
    private float tpfTime;
    private Camera camera;

    public MapState() {
        log.info("Constructed MapStateClient");
    }

    @Override
    protected void initialize(final Application app) {
        camera = app.getCamera();

        ed = getState(ConnectionState.class).getEntityData();

        am = app.getAssetManager();

        am.registerLoader(LevelLoader.class, "lvl");

        imgLoader = new AWTLoader();

        generateWangBlobInfoMap(wangBlobIndexMap);

        // arenas = ed.getEntities(FieldFilter.create(ShapeInfo.class, "id",
        // ShapeInfo.create(ShapeNames.ARENA,0,ed).getShapeId()), ShapeInfo.class,
        // BodyPosition.class);
    }

    public float getWangBlobRotations(final int indexNumber) {
        if (!wangBlobIndexMap.containsKey(indexNumber)) {
            throw new NullPointerException("WangBlobMap does not contain index number: " + indexNumber);
        }
        return wangBlobIndexMap.get(indexNumber).getRotation();
    }

    public int getWangBlobTileNumber(final int indexNumber) {
        if (!wangBlobIndexMap.containsKey(indexNumber)) {
            throw new NullPointerException("WangBlobMap does not contain index number: " + indexNumber);
        }
        return wangBlobIndexMap.get(indexNumber).getTileNumber();
    }

    private void generateWangBlobInfoMap(final HashMap<Integer, WangInfo> map) {
        // Zero
        map.put(0, new WangInfo(0, 0));
        // One
        map.put(1, new WangInfo(1, 0));
        map.put(4, new WangInfo(1, 1));
        map.put(16, new WangInfo(1, 2));
        map.put(64, new WangInfo(1, 3));
        // Five
        map.put(5, new WangInfo(2, 0));
        map.put(20, new WangInfo(2, 1));
        map.put(80, new WangInfo(2, 2));
        map.put(65, new WangInfo(2, 3));
        // Seven
        map.put(7, new WangInfo(3, 0));
        map.put(28, new WangInfo(3, 1));
        map.put(112, new WangInfo(3, 2));
        map.put(193, new WangInfo(3, 3));
        // Seventeen
        map.put(17, new WangInfo(4, 0));
        map.put(68, new WangInfo(4, 1));
        // Twentyone
        map.put(21, new WangInfo(5, 0));
        map.put(84, new WangInfo(5, 1));
        map.put(81, new WangInfo(5, 2));
        map.put(69, new WangInfo(5, 3));
        // Twentythree
        map.put(23, new WangInfo(6, 0));
        map.put(92, new WangInfo(6, 1));
        map.put(113, new WangInfo(6, 2));
        map.put(197, new WangInfo(6, 3));
        // TwentyNine
        map.put(29, new WangInfo(7, 0));
        map.put(116, new WangInfo(7, 1));
        map.put(209, new WangInfo(7, 2));
        map.put(71, new WangInfo(7, 3));
        // ThirtyOne
        map.put(31, new WangInfo(8, 0));
        map.put(124, new WangInfo(8, 1));
        map.put(241, new WangInfo(8, 2));
        map.put(199, new WangInfo(8, 3));
        // EightFive
        map.put(85, new WangInfo(9, 0));
        // EightySeven
        map.put(87, new WangInfo(10, 0));
        map.put(93, new WangInfo(10, 1));
        map.put(117, new WangInfo(10, 2));
        map.put(213, new WangInfo(10, 3));
        // NinetyFive
        map.put(95, new WangInfo(11, 0));
        map.put(125, new WangInfo(11, 1));
        map.put(245, new WangInfo(11, 2));
        map.put(215, new WangInfo(11, 3));
        // OneHundredAndNineTeen
        map.put(119, new WangInfo(12, 0));
        map.put(221, new WangInfo(12, 1));
        // OneHundredAndTwentySeven
        map.put(127, new WangInfo(13, 0));
        map.put(253, new WangInfo(13, 1));
        map.put(247, new WangInfo(13, 2));
        map.put(223, new WangInfo(13, 3));
        // TwoHundredAndFiftyFive
        map.put(255, new WangInfo(14, 0));
        // Second row
    }

    public Image getImage(final EntityId entityId) {
        return tileImages.getObject(entityId);
    }

    protected LevelFile loadMap(final String tileSet) {

        final LevelFile localMap = (LevelFile) am.loadAsset(tileSet);

        return localMap;
    }

    public EntityId getEntityId(final Vec3d coord) {
        return index.get(coord);
    }

    @Override
    protected void cleanup(final Application app) {
        return;
    }

    @Override
    protected void onEnable() {
        tileImages = new LegacyMapImageContainer(ed);
        tileImages.start();
    }

    @Override
    protected void onDisable() {
        tileImages.stop();
        tileImages = null;
    }

    @Override
    public void update(final float tpf) {
        tpfTime = tpf;

        tileImages.update();
    }

    /**
     * Converts a given Image into a BufferedImage
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    private BufferedImage toBufferedImage(final java.awt.Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        final int width = img.getWidth(null);
        final int height = img.getHeight(null);

        // Create a buffered image with transparency
        final BufferedImage bimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        final Graphics2D bGr = bimage.createGraphics();
        // bGr.drawImage(img, 0, 0, null); //No flip
        // bGr.drawImage(img, 0 + width, 0, -width, height, null); //Horisontal flip
        // bGr.drawImage(img, 0, 0 + height, width, -height, null); //Vertical flip
        bGr.drawImage(img, img.getHeight(null), 0, -img.getWidth(null), img.getHeight(null), null);

        bGr.dispose();

        // Return the buffered image
        return bimage;
    }

    public Image forceLoadImage(final EntityId id) {
        final Entity e = ed.getEntity(id, TileType.class);
        final TileType ti = e.get(TileType.class);
        final String tileSet = ti.getTileSet();
        final short tileIndex = ti.getTileIndex();
        final TileKey key = new TileKey(tileSet, tileIndex);

        if (!imageMap.containsKey(key)) {

            if (!levelFiles.containsKey(tileSet)) {
                final LevelFile lf = loadMap(tileSet); // TODO: Should be done in a non-intrusive way
                levelFiles.put(tileSet, lf);

            }

            final java.awt.Image awtInputImage = levelFiles.get(tileSet).getTiles()[tileIndex - 1];
            final Image jmeOutputImage = imgLoader.load(toBufferedImage(awtInputImage), true);
            // jmeOutputImage.dispose();

            imageMap.put(key, jmeOutputImage);

        }
        return imageMap.get(key);
    }

    // Map the entities to their texture
    private class LegacyMapImageContainer extends EntityContainer<Image> {

        public LegacyMapImageContainer(final EntityData ed) {
            super(ed, TileType.class, BodyPosition.class);
        }

        @Override
        protected Image[] getArray() {
            return super.getArray();
        }

        @Override
        protected Image addObject(final Entity e) {
            final TileType ti = e.get(TileType.class);
            final String tileSet = ti.getTileSet();
            final short tileIndex = ti.getTileIndex();
            final TileKey key = new TileKey(tileSet, tileIndex);

            if (!imageMap.containsKey(key)) {

                if (!levelFiles.containsKey(tileSet)) {
                    final LevelFile lf = loadMap(tileSet); // TODO: Should be done in a non-intrusive way
                    levelFiles.put(tileSet, lf);

                }

                final java.awt.Image awtInputImage = levelFiles.get(tileSet).getTiles()[tileIndex - 1];
                final Image jmeOutputImage = imgLoader.load(toBufferedImage(awtInputImage), true);

                imageMap.put(key, jmeOutputImage);

            }

            return imageMap.get(key);
        }

        @Override
        protected void updateObject(final Image object, final Entity e) {
            // Does not support mass updating tiles right now
            // TODO: The tileIndex in a TileInfo component could change
        }

        @Override
        protected void removeObject(final Image object, final Entity e) {
            // We leave the levels loaded
        }
    }

    public class TileKey {

        private final String tileSet;
        private final short tileIndex;

        public TileKey(final String x, final short y) {
            tileSet = x;
            tileIndex = y;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(tileSet);
            hash = 97 * hash + tileIndex;
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
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
            if (tileIndex != other.tileIndex) {
                return false;
            }
            if (!Objects.equals(tileSet, other.tileSet)) {
                return false;
            }
            return true;
        }

    }

    private class WangInfo {

        private final int tileNumber;
        private final float radianRotation;

        public WangInfo(final int tileNumber, final float rotation) {
            this.tileNumber = tileNumber;
            radianRotation = rotation;
        }

        public int getTileNumber() {
            return tileNumber;
        }

        public float getRotation() {
            return radianRotation;
        }
    }

    public void setMapEditingActive(final boolean active) {
        return;
    }

    public void addArenaMouseListeners(final Spatial arena) {

        MouseEventControl.addListenersToSpatial(arena, new DefaultMouseListener() {

            boolean isPressed;
            int keyIndex;

            private int xDown;
            private int yDown;

            @Override
            protected void click(final MouseButtonEvent event, final Spatial target, final Spatial capture) {
                return;
            }

            @Override
            public void mouseButtonEvent(final MouseButtonEvent event, final Spatial target, final Spatial capture) {
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
            public void mouseEntered(final MouseMotionEvent event, final Spatial target, final Spatial capture) {
                // Material m = ((Geometry) target).getMaterial();
                // m.setColor("Color", ColorRGBA.Yellow);
            }

            @Override
            public void mouseExited(final MouseMotionEvent event, final Spatial target, final Spatial capture) {
                // Material m = ((Geometry) target).getMaterial();
                // m.setColor("Color", ColorRGBA.Blue);
            }

            @Override
            public void mouseMoved(final MouseMotionEvent event, final Spatial target, final Spatial capture) {
                if (isPressed && keyIndex == MouseInput.BUTTON_LEFT) {
                    final GameSession session = getState(ConnectionState.class)
                            .getService(GameSessionClientService.class);
                    if (session == null) {
                        throw new RuntimeException("ModelViewState requires an active game session.");
                    }

                    final Vector2f click2d = new Vector2f(event.getX(), event.getY());

                    final Vector3f click3d = camera.getWorldCoordinates(click2d.clone(), 0f).clone();
                    final Vector3f dir = camera.getWorldCoordinates(click2d.clone(), 1f).subtractLocal(click3d)
                            .normalizeLocal();

                    final Ray ray = new Ray(click3d, dir);
                    final CollisionResults results = new CollisionResults();
                    target.collideWith(ray, results);
                    if (results.size() != 1) {
                        log.error("There should only be one collision with the arena when the user clicks it");
                    }
                    final Vector3f contactPoint = results.getCollision(0).getContactPoint();
                    session.map(MapSystem.CREATE, new Vec3d(contactPoint.x, 0, contactPoint.z));
                    // session.createTile("", contactPoint.x, contactPoint.y);
                }

                if (isPressed && keyIndex == MouseInput.BUTTON_RIGHT) {
                    final GameSession session = getState(ConnectionState.class)
                            .getService(GameSessionClientService.class);
                    if (session == null) {
                        throw new RuntimeException("ModelViewState requires an active game session.");
                    }

                    final Vector2f click2d = new Vector2f(event.getX(), event.getY());

                    final Vector3f click3d = camera.getWorldCoordinates(click2d.clone(), 0f).clone();
                    final Vector3f dir = camera.getWorldCoordinates(click2d.clone(), 1f).subtractLocal(click3d)
                            .normalizeLocal();

                    final Ray ray = new Ray(click3d, dir);
                    final CollisionResults results = new CollisionResults();
                    target.collideWith(ray, results);
                    if (results.size() != 1) {
                        log.error("There should only be one collision with the arena when the user clicks it");
                    }
                    final Vector3f contactPoint = results.getCollision(0).getContactPoint();
                    session.map(MapSystem.DELETE, new Vec3d(contactPoint.x, 0, contactPoint.y));
                    // session.removeTile(contactPoint.x, contactPoint.y);
                }
            }
        });
    }
}
