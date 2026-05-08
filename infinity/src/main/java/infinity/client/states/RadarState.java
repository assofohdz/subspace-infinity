// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.ChildPositionTransition3d;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mworld.World;
import com.simsilica.mworld.net.client.WorldClientService;
import com.simsilica.thread.JobState;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.client.view.RadarBlipFactory;
import infinity.client.view.RadarLeafSilhouetteIndex;
import infinity.client.view.RadarTheme;
import infinity.es.Frequency;
import infinity.es.arena.ArenaFootprint;
import infinity.es.RadarShapeInfo;
import infinity.es.ship.RadarRange;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Top-down radar HUD. Renders an offscreen orthographic view of {@link #radarRoot}
 * (the radar scene graph) into a {@link FrameBuffer}-backed texture, then composites
 * that texture onto the GUI through the shared {@code MatDefs/MiniMap/MiniMap.j3md}
 * material — same circle-mask + overlay treatment the legacy minimap used.
 *
 * <p>{@code radarRoot} is intentionally <b>detached</b> from the main scene graph and
 * carries two children — {@code radarEntityRoot} (entity blips, populated by the
 * body / static entity containers below) and {@code radarBlockRoot} (tile
 * silhouettes, populated by issue #04).
 *
 * <p>Entity blips:
 * <ul>
 *   <li>{@code BodyContainer} tracks {@code BodyPosition + RadarShapeInfo} —
 *       moving entities (ships, mobs). Position is driven per-frame from the
 *       {@link BodyPosition} interpolation buffer, the same SimEthereal source
 *       {@code ModelViewState.BodyContainer} uses for the world view.</li>
 *   <li>{@code StaticContainer} tracks {@code SpawnPosition + RadarShapeInfo} —
 *       fixed-position objects. Position is set once at attach time.</li>
 * </ul>
 * Spatials come from {@link RadarBlipFactory}, keyed by {@code RadarShapeInfo}'s
 * shape name (mirrors {@code SISpatialFactory}'s shape-name registry). Shape
 * names not registered fall through to a default ship triangle, so adding a new
 * ship class doesn't require a registry change.
 *
 * <p>Frequency-based coloring is resolved client-side at attach time and
 * recomputed when either the blip's {@link Frequency} or the local avatar's
 * {@link Frequency} changes:
 * <ul>
 *   <li>self → {@link RadarTheme#selfColor()}</li>
 *   <li>same team as avatar → {@link RadarTheme#friendlyColor()}</li>
 *   <li>different team → {@link RadarTheme#enemyColor()}</li>
 *   <li>no {@link Frequency} component (prizes, neutral statics) → {@link RadarTheme#neutralColor()}</li>
 * </ul>
 * Color is deliberately NOT carried on {@code RadarShapeInfo} — keeping it
 * client-side means re-skinning (color-blind palettes, themes) is purely a
 * client concern with no server change.
 *
 * <p>Camera follows the local avatar:
 * <ul>
 *   <li>Position from the SimEthereal-interpolated {@link BodyPosition} buffer (the
 *       same source {@code AvatarMovementState} drives the world camera from), so the
 *       radar tracks the visible ship rather than chasing a stale RMI snapshot.</li>
 *   <li>Frustum half-extent from the avatar's {@link RadarRange} component — recomputed
 *       on every change, so swapping ships (or live-tuning {@code radarRange} in the
 *       Groovy preset) immediately rescales the view.</li>
 * </ul>
 *
 * <p>Avatar resolution is lazy in {@link #update}: {@code GameSessionState} fetches the
 * id via an RMI roundtrip and the result may not have arrived by {@code initialize()}.
 * Per {@code feedback_client_ecs_reads}, single-entity component reads go through
 * {@code ed.watchEntity}; {@code ed.getComponent} on the client-side proxy is unreliable
 * for components the client isn't otherwise observing.
 *
 * <p>Read-only by design: the radar observes server-authored components and never writes
 * back. Per {@code .claude/rules/client-read-only.md}.
 *
 * @author Asser Fahrenholz
 */
public class RadarState extends BaseAppState {

    private static final float RADAR_CAM_HEIGHT = 1000f;
    private static final float RADAR_CAM_NEAR = 1f;
    private static final float RADAR_CAM_FAR = 5000f;
    private static final double DEFAULT_RANGE_WORLD_UNITS = 256.0;

    /**
     * Palette used by the radar — colours read here flow into the off-screen
     * viewport background, the silhouette material, and per-blip tinting.
     * {@link RadarTheme#DEFAULT} is the shipping look; a future HUD-theming
     * system can swap this out (constructor inject / setter / blackboard).
     */
    private final RadarTheme theme = RadarTheme.DEFAULT;

    private Node radarRoot;
    private Node radarEntityRoot;
    private Node radarBlockRoot;
    private Node footprintRoot;

    private Camera radarCam;
    private ViewPort radarViewport;

    private Geometry radarQuad;
    private Node guiNode;

    private EntityData ed;
    private TimeSource timeSource;
    private EntityId avatarEntityId;
    private WatchedEntity avatarWatch;
    private BodyPosition avatarBodyPos;
    private Integer currentAvatarFreq;
    private double currentRange = -1.0;

    private RadarBlipFactory blipFactory;
    private BodyContainer bodies;
    private StaticContainer statics;
    private ArenaFootprintContainer footprints;
    private EntitySet frequencies;
    private final Map<EntityId, Blip> blipsById = new HashMap<>();
    private float blipScale = 1f;

    /**
     * Owns the leaf-cache, the {@link com.simsilica.mworld.LeafChangeListener},
     * the disc-walk view array, and the per-tick paging math. Constructed in
     * {@link #initialize(Application)} after world / silhouetteIndex / worker
     * pools resolve; disposed from {@link #cleanup(Application)}. See
     * {@link RadarLeafPager} for the moved state and methods.
     */
    private RadarLeafPager pager;

    private final Vector3f camLocation = new Vector3f();

    public Node getRadarRoot() {
        return radarRoot;
    }

    public Node getRadarEntityRoot() {
        return radarEntityRoot;
    }

    public Node getRadarBlockRoot() {
        return radarBlockRoot;
    }

    @Override
    protected void initialize(final Application app) {
        ed = getState(ConnectionState.class).getEntityData();
        timeSource = getState(ConnectionState.class).getRemoteTimeSource();
        guiNode = ((SimpleApplication) app).getGuiNode();
        blipFactory = new RadarBlipFactory(app.getAssetManager(), theme);
        final RadarLeafSilhouetteIndex silhouetteIndex =
                new RadarLeafSilhouetteIndex(app.getAssetManager(), theme);

        // World + worker pools — same lookups LocalViewState uses; the radar
        // shares the existing job-state services (and their thread budgets)
        // rather than spinning up its own pool.
        final World world = getState(ConnectionState.class).getService(WorldClientService.class);
        final JobState workers = getState("regularWorkers", JobState.class);
        final JobState priorityWorkers = getState("priorityWorkers", JobState.class);

        radarRoot = new Node("RadarRoot");
        radarEntityRoot = new Node("RadarEntityRoot");
        radarBlockRoot = new Node("RadarBlockRoot");
        footprintRoot = new Node("ArenaFootprintRoot");
        radarRoot.attachChild(radarEntityRoot);
        radarRoot.attachChild(radarBlockRoot);
        radarRoot.attachChild(footprintRoot);

        // Construct + wire the leaf-paging subsystem now that all its
        // dependencies are resolved. attachLeafObserver() registers the
        // LeafChangeListener — same registration the inline init did.
        pager = new RadarLeafPager(world, workers, priorityWorkers, silhouetteIndex, radarBlockRoot);
        pager.attachLeafObserver();

        bodies = new BodyContainer(ed);
        statics = new StaticContainer(ed);
        footprints = new ArenaFootprintContainer(ed);
        frequencies = ed.getEntities(RadarShapeInfo.class, Frequency.class);

        radarCam = new Camera(theme.pixelSize(), theme.pixelSize());
        radarCam.setParallelProjection(true);
        // Frustum is sized from RadarRange in update(); seed with the default so the
        // first off-screen pass before the watch resolves still has a sane projection.
        applyRange(DEFAULT_RANGE_WORLD_UNITS);
        radarCam.setLocation(new Vector3f(0f, RADAR_CAM_HEIGHT, 0f));
        // Look straight down (-Y) with world +Z as "up" on the radar — keeps north fixed,
        // i.e. the radar does not rotate with the player heading.
        radarCam.lookAtDirection(new Vector3f(0f, -1f, 0f), Vector3f.UNIT_Z);

        radarViewport = app.getRenderManager().createPreView("RadarOffscreen", radarCam);
        radarViewport.setClearFlags(true, true, true);
        radarViewport.setBackgroundColor(theme.voidTintColor());
        radarViewport.attachScene(radarRoot);

        final Texture2D radarTex = new Texture2D(theme.pixelSize(), theme.pixelSize(), Image.Format.RGBA8);
        radarTex.setMinFilter(Texture.MinFilter.Trilinear);
        radarTex.setMagFilter(Texture.MagFilter.Bilinear);

        final FrameBuffer radarFrameBuffer = new FrameBuffer(theme.pixelSize(), theme.pixelSize(), 1);
        radarFrameBuffer.setDepthTarget(FrameBuffer.FrameBufferTarget.newTarget(Image.Format.Depth));
        radarFrameBuffer.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(radarTex));
        radarViewport.setOutputFrameBuffer(radarFrameBuffer);

        radarQuad = new Geometry("Radar", new Quad(theme.pixelSize(), theme.pixelSize()));
        final Material mat = new Material(app.getAssetManager(), "MatDefs/MiniMap/MiniMap.j3md");
        mat.setTexture("ColorMap", radarTex);
        mat.setTexture("Mask", app.getAssetManager().loadTexture("Textures/MiniMap/circle-mask.png"));
        mat.setTexture("Overlay",
                app.getAssetManager().loadTexture("Textures/MiniMap/circle-overlay.png"));
        radarQuad.setMaterial(mat);
        // Bottom-right, flush against right + bottom screen edges.
        radarQuad.setLocalTranslation(
                app.getCamera().getWidth() - theme.pixelSize(),
                0f,
                1f);
    }

    @Override
    protected void cleanup(final Application app) {
        if (radarViewport != null) {
            app.getRenderManager().removePreView(radarViewport);
            radarViewport = null;
        }
        if (avatarWatch != null) {
            avatarWatch.release();
            avatarWatch = null;
        }
        if (frequencies != null) {
            frequencies.release();
            frequencies = null;
        }
        if (pager != null) {
            pager.dispose();
            pager = null;
        }
        avatarBodyPos = null;
    }

    @Override
    protected void onEnable() {
        guiNode.attachChild(radarQuad);
        bodies.start();
        statics.start();
        footprints.start();
    }

    @Override
    protected void onDisable() {
        radarQuad.removeFromParent();
        bodies.stop();
        statics.stop();
        footprints.stop();
    }

    @Override
    public void update(final float tpf) {
        resolveAvatar();
        applyAvatarFreqChanges();
        updateRangeAndPosition();

        bodies.update();
        statics.update();
        footprints.update();
        applyFrequencyChanges();
        updateBodyBlipPositions();

        if (pager != null) {
            pager.drainLeafUpdates();
        }

        // radarRoot is not part of rootNode/guiNode, so the engine doesn't update its
        // world transforms for us. Drive it directly so children added by the leaf-
        // silhouette and entity-blip pipelines render with up-to-date matrices.
        radarRoot.updateLogicalState(tpf);
        radarRoot.updateGeometricState();
    }

    private void resolveAvatar() {
        if (avatarEntityId == null) {
            final EntityId id = getState(GameSessionState.class).getAvatarEntityId();
            if (id == null || EntityId.NULL_ID.equals(id)) {
                return;
            }
            avatarEntityId = id;
            // Once the avatar id resolves, the local-player-vs-everyone-else colour
            // partition shifts: the blip already attached for our own ship was painted
            // with its own-frequency colour. Repaint everything now so the self blip
            // flips to theme.selfColor().
            recolorAllBlips();
        }
        if (avatarWatch == null) {
            avatarWatch = ed.watchEntity(
                    avatarEntityId, BodyPosition.class, RadarRange.class, Frequency.class);
            final BodyPosition bp = avatarWatch.get(BodyPosition.class);
            if (bp != null) {
                bp.initialize(avatarEntityId, 12);
                avatarBodyPos = bp;
            }
            final Frequency f = avatarWatch.get(Frequency.class);
            if (f != null) {
                currentAvatarFreq = f.getFrequency();
                recolorAllBlips();
            }
        }
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals") // BodyPosition reference uniqueness check
    private void applyAvatarFreqChanges() {
        if (avatarWatch == null) {
            return;
        }
        if (!avatarWatch.applyChanges()) {
            return;
        }
        final BodyPosition bp = avatarWatch.get(BodyPosition.class);
        if (bp != null && bp != avatarBodyPos) {
            bp.initialize(avatarEntityId, 12);
            avatarBodyPos = bp;
        }
        final Frequency f = avatarWatch.get(Frequency.class);
        final Integer newFreq = f != null ? f.getFrequency() : null;
        if (!Objects.equals(newFreq, currentAvatarFreq)) {
            currentAvatarFreq = newFreq;
            recolorAllBlips();
        }
    }

    private void updateRangeAndPosition() {
        if (avatarWatch == null) {
            return;
        }
        applyAvatarRange();
        final Vec3d pos = resolveAvatarWorldPosition();
        if (pos == null) {
            return;
        }
        camLocation.set((float) pos.x, RADAR_CAM_HEIGHT, (float) pos.z);
        radarCam.setLocation(camLocation);
        if (pager != null) {
            pager.updateLeafPaging(pos, currentRange);
        }
    }

    private void applyAvatarRange() {
        final RadarRange range = avatarWatch.get(RadarRange.class);
        final double r = (range != null && range.getRange() > 0)
                ? range.getRange()
                : DEFAULT_RANGE_WORLD_UNITS;
        if (r != currentRange) {
            applyRange(r);
        }
    }

    private Vec3d resolveAvatarWorldPosition() {
        if (avatarBodyPos == null || timeSource == null) {
            return null;
        }
        final long t = timeSource.getTime();
        final ChildPositionTransition3d frame = avatarBodyPos.getFrame(t);
        if (frame == null) {
            return null;
        }
        return frame.getPosition(t, true);
    }

    private void applyFrequencyChanges() {
        if (!frequencies.applyChanges()) {
            return;
        }
        for (final Entity e : frequencies.getAddedEntities()) {
            final Blip blip = blipsById.get(e.getId());
            if (blip != null) {
                blip.freq = e.get(Frequency.class).getFrequency();
                applyColor(blip);
            }
        }
        for (final Entity e : frequencies.getChangedEntities()) {
            final Blip blip = blipsById.get(e.getId());
            if (blip != null) {
                blip.freq = e.get(Frequency.class).getFrequency();
                applyColor(blip);
            }
        }
        for (final Entity e : frequencies.getRemovedEntities()) {
            final Blip blip = blipsById.get(e.getId());
            if (blip != null) {
                blip.freq = null;
                applyColor(blip);
            }
        }
    }

    private void updateBodyBlipPositions() {
        if (timeSource == null) {
            return;
        }
        final long t = timeSource.getTime();
        for (final Blip blip : bodies.getArray()) {
            if (blip.bodyPos == null) {
                continue;
            }
            final ChildPositionTransition3d trans = blip.bodyPos.getFrame(t);
            if (trans == null) {
                continue;
            }
            final Vec3d pos = trans.getPosition(t, true);
            if (pos == null) {
                continue;
            }
            blip.geom.setLocalTranslation((float) pos.x, 0f, (float) pos.z);
        }
    }

    private void applyRange(final double rangeWorldUnits) {
        final float half = (float) rangeWorldUnits;
        radarCam.setFrustum(RADAR_CAM_NEAR, RADAR_CAM_FAR, -half, half, half, -half);
        currentRange = rangeWorldUnits;

        // Keep blip on-screen pixel size constant as the radar zoom changes:
        // scaling each blip's world size proportional to the range cancels the
        // change in camera frustum, so the blip stays the same fraction of the
        // radar circle no matter how far we're zoomed out.
        final float newScale = (float) (rangeWorldUnits / theme.canonicalRangeWorldUnits());
        if (newScale != blipScale) {
            blipScale = newScale;
            for (final Blip blip : blipsById.values()) {
                blip.geom.setLocalScale(blipScale);
            }
        }
    }

    private ColorRGBA colorFor(final EntityId id, final Integer entityFreq) {
        return RadarStateLogic.colorFor(id, entityFreq, avatarEntityId, currentAvatarFreq, theme);
    }

    private void applyColor(final Blip blip) {
        blip.geom.getMaterial().setColor("Color", colorFor(blip.id, blip.freq));
    }

    private void recolorAllBlips() {
        for (final Blip blip : blipsById.values()) {
            applyColor(blip);
        }
    }

    /**
     * Get-or-create the single Blip for an entity. Both {@link BodyContainer} and
     * {@link StaticContainer} can match the same entity (e.g. the local ship has
     * both {@link BodyPosition} and {@link SpawnPosition}); reference counting
     * keeps the blip alive until both containers drop it. {@code bodyPos != null}
     * marks the blip as body-driven, in which case {@code SpawnPosition} updates
     * are ignored — body position always wins.
     */
    private Blip acquireBlip(final Entity e) {
        Blip blip = blipsById.get(e.getId());
        if (blip == null) {
            final RadarShapeInfo info = e.get(RadarShapeInfo.class);
            final Geometry geom = blipFactory.create(info.getShapeName(ed), theme.neutralColor());
            geom.setLocalScale(blipScale);
            blip = new Blip(e.getId(), geom);
            // Seed colour from the freq set if it already knows about this entity —
            // otherwise the entity has no Frequency yet (or applyChanges hasn't run
            // for it), and applyFrequencyChanges() will repaint it on a later tick.
            final Entity freqEntity = frequencies.getEntity(e.getId());
            if (freqEntity != null) {
                blip.freq = freqEntity.get(Frequency.class).getFrequency();
            }
            applyColor(blip);
            radarEntityRoot.attachChild(geom);
            blipsById.put(e.getId(), blip);
        }
        blip.useCount++;
        return blip;
    }

    private void releaseBlip(final EntityId id) {
        final Blip blip = blipsById.get(id);
        if (blip == null) {
            return;
        }
        blip.useCount--;
        if (blip.useCount <= 0) {
            blipsById.remove(id);
            blip.geom.removeFromParent();
        }
    }

    private static final class Blip {
        final EntityId id;
        final Geometry geom;
        BodyPosition bodyPos;
        Integer freq;
        int useCount;

        Blip(final EntityId id, final Geometry geom) {
            this.id = id;
            this.geom = geom;
        }
    }

    private final class BodyContainer extends EntityContainer<Blip> {
        BodyContainer(final EntityData ed) {
            super(ed, BodyPosition.class, RadarShapeInfo.class);
        }

        @Override
        public Blip[] getArray() {
            return super.getArray();
        }

        @Override
        protected Blip addObject(final Entity e) {
            final Blip blip = acquireBlip(e);
            final BodyPosition bp = e.get(BodyPosition.class);
            // Same idempotent initialize convention as ModelViewState — required so
            // the ring buffer is allocated against this entity id.
            bp.initialize(e.getId(), 12);
            blip.bodyPos = bp;
            return blip;
        }

        @Override
        protected void updateObject(final Blip blip, final Entity e) {
            // Position ticks are pushed each frame from updateBodyBlipPositions().
            // Shape doesn't change after spawn, so nothing to do here.
        }

        @Override
        protected void removeObject(final Blip blip, final Entity e) {
            blip.bodyPos = null;
            releaseBlip(e.getId());
        }
    }

    private final class StaticContainer extends EntityContainer<Blip> {
        StaticContainer(final EntityData ed) {
            super(ed, SpawnPosition.class, RadarShapeInfo.class);
        }

        @Override
        protected Blip addObject(final Entity e) {
            final Blip blip = acquireBlip(e);
            // Only place a static-driven blip if a body isn't already driving it —
            // otherwise we'd snap the avatar back to its spawn point each frame.
            if (blip.bodyPos == null) {
                setStaticPosition(blip, e.get(SpawnPosition.class));
            }
            return blip;
        }

        @Override
        protected void updateObject(final Blip blip, final Entity e) {
            if (blip.bodyPos == null) {
                setStaticPosition(blip, e.get(SpawnPosition.class));
            }
        }

        @Override
        protected void removeObject(final Blip blip, final Entity e) {
            releaseBlip(e.getId());
        }

        private void setStaticPosition(final Blip blip, final SpawnPosition pos) {
            final Vec3d loc = pos.getLocation();
            blip.geom.setLocalTranslation((float) loc.x, 0f, (float) loc.z);
        }
    }

    /**
     * Slice U1 — ArenaFootprint entities (today: arenas) get a closed-polygon
     * footprint on the radar. Two child geometries per footprint: a
     * triangulated interior fill (arenaTintColor) and a Mesh.Mode.Lines
     * outline (arenaOutlineColor). Both share the polygon's vertices;
     * Y-offsets layer fill behind outline behind blips so the existing
     * entity-blip layer stays on top.
     *
     * <p>Footprint geometry is immutable per components.md — vertices don't
     * change while an ArenaFootprint exists, so updateObject is a no-op. If
     * a future entity replaces its ArenaFootprint with new vertices,
     * removeObject + addObject will rebuild.
     */
    private final class ArenaFootprintContainer extends EntityContainer<Node> {
        // Y-offsets layer geometries within the radar's top-down view: blips
        // sit at Y=0, so outlines (-1) and fills (-2) render behind them with
        // the orthographic camera looking down -Y from Y=1000.
        private static final float OUTLINE_Y = -1f;
        private static final float FILL_Y = -2f;

        @SuppressWarnings("unchecked")
        ArenaFootprintContainer(final EntityData ed) {
            super(ed, ArenaFootprint.class);
        }

        @Override
        protected Node addObject(final Entity e) {
            final ArenaFootprint footprint = e.get(ArenaFootprint.class);
            final Vec3d[] verts = footprint.getVertices();
            final Node node = new Node("ArenaFootprint-" + e.getId());
            if (verts != null && verts.length >= 3) {
                node.attachChild(buildFootprintFill(verts));
                node.attachChild(buildFootprintOutline(verts));
            }
            footprintRoot.attachChild(node);
            return node;
        }

        @Override
        protected void updateObject(final Node node, final Entity e) {
            // ArenaFootprint vertices are immutable; no-op.
        }

        @Override
        protected void removeObject(final Node node, final Entity e) {
            node.removeFromParent();
        }
    }

    /** Thin delegate to {@link RadarStateLogic#buildFootprintFill}. */
    private Geometry buildFootprintFill(final Vec3d[] verts) {
        return RadarStateLogic.buildFootprintFill(
            verts,
            theme.arenaTintColor(),
            getApplication().getAssetManager(),
            ArenaFootprintContainer.FILL_Y);
    }

    /** Thin delegate to {@link RadarStateLogic#buildFootprintOutline}. */
    private Geometry buildFootprintOutline(final Vec3d[] verts) {
        return RadarStateLogic.buildFootprintOutline(
            verts,
            theme.arenaOutlineColor(),
            getApplication().getAssetManager(),
            ArenaFootprintContainer.OUTLINE_Y);
    }

    // RadarLeafView, RadarLeafObserver, RadarViewEntry moved to RadarLeafPager.
}
