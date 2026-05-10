// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.simsilica.bpos.BodyPosition;
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
import infinity.es.TileType;
import infinity.map.BitmapData;
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
    private final Map<String, LevelFile> levelFiles = new HashMap<>();
    private AWTLoader imgLoader;
    private final Map<TileKey, Image> imageMap = new HashMap<>();

    private final Map<Integer, WangInfo> wangBlobIndexMap = new HashMap<>();
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
            throw new IllegalArgumentException("WangBlobMap does not contain index number: " + indexNumber);
        }
        return wangBlobIndexMap.get(Integer.valueOf(indexNumber)).getRotation();
    }

    public int getWangBlobTileNumber(final int indexNumber) {
        if (!wangBlobIndexMap.containsKey(Integer.valueOf(indexNumber))) {
            throw new IllegalArgumentException("WangBlobMap does not contain index number: " + indexNumber);
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

    // Subspace tileset geometry: 304×160 BMP holds a 19×10 grid of 16×16 tiles.
    private static final int TILE_SIZE = 16;
    private static final int TILES_PER_ROW = 19;

    /**
     * Slices the 16×16 tile at {@code tileIndex} (1-based on disk) out of the
     * given tileset bitmap.
     */
    private BitmapData sliceTile(final BitmapData tileset, final short tileIndex) {
        final int t = tileIndex - 1; // disk uses 1-based indexing
        return tileset.subRegion(
                (t % TILES_PER_ROW) * TILE_SIZE,
                (t / TILES_PER_ROW) * TILE_SIZE,
                TILE_SIZE,
                TILE_SIZE);
    }

    /**
     * Converts a {@link BitmapData} into a {@link BufferedImage} feedable to
     * {@code AWTLoader}. Applies the legacy horizontal flip so JME texture
     * orientation matches the historical Subspace bitmap convention. Assumes
     * the input is square (w == h) — true for the 16×16 tile crops this is
     * called on.
     */
    private BufferedImage toBufferedImage(final BitmapData data) {
        final int w = data.width();
        final int h = data.height();

        // Build a non-flipped BufferedImage from the raw ARGB pixels.
        final BufferedImage source = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, w, h, data.argb(), 0, w);

        // Apply the legacy horizontal flip via Graphics2D — preserves the exact
        // dst-rect math (x=h, w=-w) the AWT-Image pipeline used.
        final BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = flipped.createGraphics();
        g.drawImage(source, h, 0, -w, h, null);
        g.dispose();
        return flipped;
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

            final BitmapData tileBitmap = sliceTile(levelFiles.get(tileSet).getTileset(), tileIndex);
            final Image jmeOutputImage = imgLoader.load(toBufferedImage(tileBitmap), true);
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

                final BitmapData tileBitmap = sliceTile(levelFiles.get(tileSet).getTileset(), tileIndex);
                final Image jmeOutputImage = imgLoader.load(toBufferedImage(tileBitmap), true);

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
                    final GameSession session = requireGameSession();
                    final Vector3f contactPoint = rayCastClickToArena(event, target);
                    session.map(MapSystem.CREATE, new Vec3d(contactPoint.x, 0, contactPoint.z));
                    // session.createTile("", contactPoint.x, contactPoint.y);
                }

                if (isPressed && keyIndex == MouseInput.BUTTON_RIGHT) {
                    final GameSession session = requireGameSession();
                    final Vector3f contactPoint = rayCastClickToArena(event, target);
                    session.map(MapSystem.DELETE, new Vec3d(contactPoint.x, 0, contactPoint.y));
                    // session.removeTile(contactPoint.x, contactPoint.y);
                }
            }
        });
    }

    /**
     * Resolves the active {@link GameSession} from the client services, or
     * throws if no session is hosted. Extracted so the per-mouse-button branches
     * in the {@code addArenaMouseListeners} listener don't each need their own
     * lookup-and-null-check pair.
     */
    private GameSession requireGameSession() {
        final GameSession session = getState(ConnectionState.class)
                .getService(GameSessionClientService.class);
        if (session == null) {
            throw new IllegalStateException("ModelViewState requires an active game session.");
        }
        return session;
    }

    /**
     * Casts a screen-space click ray onto {@code target}'s collision geometry
     * and returns the first collision point. Logs (but does not throw) when the
     * ray hits the arena more than once. Extracted from the {@code mouseMoved}
     * branches in {@code addArenaMouseListeners} so each branch only carries
     * its own button-specific {@code session.map(...)} dispatch.
     */
    private Vector3f rayCastClickToArena(final MouseMotionEvent event, final Spatial target) {
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
        return results.getCollision(0).getContactPoint();
    }
}
