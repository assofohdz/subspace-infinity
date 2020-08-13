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
import java.util.Map;
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

    // AndFilter andFilter;
    private EntityData ed;
    private LegacyMapImageContainer tileImages;
    private final java.util.Map<Vec3d, EntityId> index = new ConcurrentHashMap<>();
    private AssetManager am;
    private final HashMap<String, LevelFile> levelFiles = new HashMap<>();
    private AWTLoader imgLoader;
    private final HashMap<TileKey, Image> imageMap = new HashMap<>();

    private final HashMap<Integer, WangInfo> wangBlobIndexMap = new HashMap<>();
    // private float tpfTime;
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
        if (!wangBlobIndexMap.containsKey(Integer.valueOf(indexNumber))) {
            throw new NullPointerException("WangBlobMap does not contain index number: " + indexNumber);
        }
        return wangBlobIndexMap.get(Integer.valueOf(indexNumber)).getRotation();
    }

    public int getWangBlobTileNumber(final int indexNumber) {
        if (!wangBlobIndexMap.containsKey(Integer.valueOf(indexNumber))) {
            throw new NullPointerException("WangBlobMap does not contain index number: " + indexNumber);
        }
        return wangBlobIndexMap.get(Integer.valueOf(indexNumber)).getTileNumber();
    }

    private void addWang(final Map<Integer, WangInfo> map, final int key, final int tileNumber, final float rotation) {
        map.put(Integer.valueOf(key), new WangInfo(tileNumber, rotation));
    }

    private void generateWangBlobInfoMap(final Map<Integer, WangInfo> map) {
        // Zero
        addWang(map, 0, 0, 0);
        // One
        addWang(map, 1, 1, 0);
        addWang(map, 4, 1, 1);
        addWang(map, 16, 1, 2);
        addWang(map, 64, 1, 3);
        // Five
        addWang(map, 5, 2, 0);
        addWang(map, 20, 2, 1);
        addWang(map, 80, 2, 2);
        addWang(map, 65, 2, 3);
        // Seven
        addWang(map, 7, 3, 0);
        addWang(map, 28, 3, 1);
        addWang(map, 112, 3, 2);
        addWang(map, 193, 3, 3);
        // Seventeen
        addWang(map, 17, 4, 0);
        addWang(map, 68, 4, 1);
        // Twentyone
        addWang(map, 21, 5, 0);
        addWang(map, 84, 5, 1);
        addWang(map, 81, 5, 2);
        addWang(map, 69, 5, 3);
        // Twentythree
        addWang(map, 23, 6, 0);
        addWang(map, 92, 6, 1);
        addWang(map, 113, 6, 2);
        addWang(map, 197, 6, 3);
        // TwentyNine
        addWang(map, 29, 7, 0);
        addWang(map, 116, 7, 1);
        addWang(map, 209, 7, 2);
        addWang(map, 71, 7, 3);
        // ThirtyOne
        addWang(map, 31, 8, 0);
        addWang(map, 124, 8, 1);
        addWang(map, 241, 8, 2);
        addWang(map, 199, 8, 3);
        // EightFive
        addWang(map, 85, 9, 0);
        // EightySeven
        addWang(map, 87, 10, 0);
        addWang(map, 93, 10, 1);
        addWang(map, 117, 10, 2);
        addWang(map, 213, 10, 3);
        // NinetyFive
        addWang(map, 95, 11, 0);
        addWang(map, 125, 11, 1);
        addWang(map, 245, 11, 2);
        addWang(map, 215, 11, 3);
        // OneHundredAndNineTeen
        addWang(map, 119, 12, 0);
        addWang(map, 221, 12, 1);
        // OneHundredAndTwentySeven
        addWang(map, 127, 13, 0);
        addWang(map, 253, 13, 1);
        addWang(map, 247, 13, 2);
        addWang(map, 223, 13, 3);
        // TwoHundredAndFiftyFive
        addWang(map, 255, 14, 0);
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
        // tpfTime = tpf;
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

        @SuppressWarnings("unchecked")
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

    public void setMapEditingActive(@SuppressWarnings("unused") final boolean active) {
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
