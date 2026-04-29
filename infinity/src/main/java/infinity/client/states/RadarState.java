/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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
import com.simsilica.mathd.Vec3i;
import com.simsilica.mworld.LeafChangeEvent;
import com.simsilica.mworld.LeafChangeListener;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.LeafId;
import com.simsilica.mworld.World;
import com.simsilica.mworld.WorldGrids;
import com.simsilica.mworld.net.client.WorldClientService;
import com.simsilica.thread.Job;
import com.simsilica.thread.JobState;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.client.view.RadarBlipFactory;
import infinity.client.view.RadarLeafSilhouetteIndex;
import infinity.es.Frequency;
import infinity.es.RadarShapeInfo;
import infinity.es.ship.RadarRange;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

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
 *   <li>self → {@link #SELF_COLOR}</li>
 *   <li>same team as avatar → {@link #FRIENDLY_COLOR}</li>
 *   <li>different team → {@link #ENEMY_COLOR}</li>
 *   <li>no {@link Frequency} component (prizes, neutral statics) → {@link #NEUTRAL_COLOR}</li>
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

    private static final int RADAR_PIXEL_SIZE = 218;
    private static final float RADAR_CAM_HEIGHT = 1000f;
    private static final float RADAR_CAM_NEAR = 1f;
    private static final float RADAR_CAM_FAR = 5000f;
    private static final double DEFAULT_RANGE_WORLD_UNITS = 256.0;
    /**
     * The radar range (world units) at which {@link RadarBlipFactory} mesh sizes
     * are 1:1 with the rendered pixel size. Blips are scaled by
     * {@code currentRange / RADAR_CANONICAL_RANGE} so a blip stays the same on-
     * screen size regardless of how far the radar is zoomed out — the blip only
     * needs to respect the radar circle, not the world distances inside it.
     * Map silhouettes are NOT scaled — they're real map geometry.
     */
    private static final double RADAR_CANONICAL_RANGE = 256.0;

    private static final ColorRGBA SELF_COLOR = ColorRGBA.White;
    private static final ColorRGBA FRIENDLY_COLOR = ColorRGBA.Green;
    private static final ColorRGBA ENEMY_COLOR = ColorRGBA.Red;
    private static final ColorRGBA NEUTRAL_COLOR = ColorRGBA.Gray;
    /** Ambient background inside the radar circle — muddy dark green à la Continuum. */
    private static final ColorRGBA RADAR_BACKGROUND_COLOR = new ColorRGBA(0.12f, 0.20f, 0.10f, 1f);

    private Node radarRoot;
    private Node radarEntityRoot;
    private Node radarBlockRoot;

    private Camera radarCam;
    private ViewPort radarViewport;
    private FrameBuffer radarFrameBuffer;
    private Texture2D radarTex;

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
    private EntitySet frequencies;
    private final Map<EntityId, Blip> blipsById = new HashMap<>();
    private float blipScale = 1f;

    private World world;
    private JobState workers;
    private JobState priorityWorkers;
    private RadarLeafSilhouetteIndex silhouetteIndex;
    private final Map<LeafId, RadarLeafView> radarLeafCache = new HashMap<>();
    private final ConcurrentLinkedQueue<LeafId> radarUpdatedLeafIds = new ConcurrentLinkedQueue<>();
    private RadarLeafObserver radarLeafObserver;
    private RadarViewEntry[] radarViewArray;
    private final Vec3i radarCenterCell = new Vec3i(0, 100, 0); // sentinel — no cell will match
    private int radarLeafRadius = -1;

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
        blipFactory = new RadarBlipFactory(app.getAssetManager());
        silhouetteIndex = new RadarLeafSilhouetteIndex(app.getAssetManager());

        // World + worker pools — same lookups LocalViewState uses; the radar
        // shares the existing job-state services (and their thread budgets)
        // rather than spinning up its own pool.
        world = getState(ConnectionState.class).getService(WorldClientService.class);
        workers = getState("regularWorkers", JobState.class);
        priorityWorkers = getState("priorityWorkers", JobState.class);
        radarLeafObserver = new RadarLeafObserver();
        world.addLeafChangeListener(radarLeafObserver);

        radarRoot = new Node("RadarRoot");
        radarEntityRoot = new Node("RadarEntityRoot");
        radarBlockRoot = new Node("RadarBlockRoot");
        radarRoot.attachChild(radarEntityRoot);
        radarRoot.attachChild(radarBlockRoot);

        bodies = new BodyContainer(ed);
        statics = new StaticContainer(ed);
        frequencies = ed.getEntities(RadarShapeInfo.class, Frequency.class);

        radarCam = new Camera(RADAR_PIXEL_SIZE, RADAR_PIXEL_SIZE);
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
        radarViewport.setBackgroundColor(RADAR_BACKGROUND_COLOR);
        radarViewport.attachScene(radarRoot);

        radarTex = new Texture2D(RADAR_PIXEL_SIZE, RADAR_PIXEL_SIZE, Image.Format.RGBA8);
        radarTex.setMinFilter(Texture.MinFilter.Trilinear);
        radarTex.setMagFilter(Texture.MagFilter.Bilinear);

        radarFrameBuffer = new FrameBuffer(RADAR_PIXEL_SIZE, RADAR_PIXEL_SIZE, 1);
        radarFrameBuffer.setDepthTarget(FrameBuffer.FrameBufferTarget.newTarget(Image.Format.Depth));
        radarFrameBuffer.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(radarTex));
        radarViewport.setOutputFrameBuffer(radarFrameBuffer);

        radarQuad = new Geometry("Radar", new Quad(RADAR_PIXEL_SIZE, RADAR_PIXEL_SIZE));
        final Material mat = new Material(app.getAssetManager(), "MatDefs/MiniMap/MiniMap.j3md");
        mat.setTexture("ColorMap", radarTex);
        mat.setTexture("Mask", app.getAssetManager().loadTexture("Textures/MiniMap/circle-mask.png"));
        mat.setTexture("Overlay",
                app.getAssetManager().loadTexture("Textures/MiniMap/circle-overlay.png"));
        radarQuad.setMaterial(mat);
        // Bottom-right, flush against right + bottom screen edges.
        radarQuad.setLocalTranslation(
                app.getCamera().getWidth() - RADAR_PIXEL_SIZE,
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
        if (world != null && radarLeafObserver != null) {
            world.removeLeafChangeListener(radarLeafObserver);
            radarLeafObserver = null;
        }
        for (final RadarLeafView view : radarLeafCache.values()) {
            view.release();
            workers.cancel(view);
        }
        radarLeafCache.clear();
        avatarBodyPos = null;
    }

    @Override
    protected void onEnable() {
        guiNode.attachChild(radarQuad);
        bodies.start();
        statics.start();
    }

    @Override
    protected void onDisable() {
        radarQuad.removeFromParent();
        bodies.stop();
        statics.stop();
    }

    @Override
    public void update(final float tpf) {
        resolveAvatar();
        applyAvatarFreqChanges();
        updateRangeAndPosition();

        bodies.update();
        statics.update();
        applyFrequencyChanges();
        updateBodyBlipPositions();

        drainLeafUpdates();

        // radarRoot is not part of rootNode/guiNode, so the engine doesn't update its
        // world transforms for us. Drive it directly so children added by the leaf-
        // silhouette and entity-blip pipelines render with up-to-date matrices.
        radarRoot.updateLogicalState(tpf);
        radarRoot.updateGeometricState();
    }

    /**
     * Apply any leaf-change events the observer queued since last frame. If we are
     * currently paging the changed leaf, requeue its job at top priority so the
     * silhouette refreshes promptly (e.g. a wall got built or destroyed server-side).
     */
    private void drainLeafUpdates() {
        LeafId leafId;
        while ((leafId = radarUpdatedLeafIds.poll()) != null) {
            final RadarLeafView view = radarLeafCache.get(leafId);
            if (view != null) {
                priorityWorkers.execute(view, -1);
            }
        }
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
            // as ENEMY/NEUTRAL using its own frequency. Repaint everything now so the
            // self blip flips to SELF_COLOR.
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
        final RadarRange range = avatarWatch.get(RadarRange.class);
        final double r = (range != null && range.getRange() > 0)
                ? range.getRange()
                : DEFAULT_RANGE_WORLD_UNITS;
        if (r != currentRange) {
            applyRange(r);
            // Range change → paging radius derived from RadarRange must be re-evaluated.
            // updateLeafPaging() picks up the new currentRange below.
        }
        if (avatarBodyPos == null || timeSource == null) {
            return;
        }
        final long t = timeSource.getTime();
        final ChildPositionTransition3d frame = avatarBodyPos.getFrame(t);
        if (frame == null) {
            return;
        }
        final Vec3d pos = frame.getPosition(t, true);
        if (pos == null) {
            return;
        }
        camLocation.set((float) pos.x, RADAR_CAM_HEIGHT, (float) pos.z);
        radarCam.setLocation(camLocation);
        updateLeafPaging(pos);
    }

    /**
     * Page leaf silhouettes in / out around the avatar. Mirrors
     * {@code LocalViewState.updateView}, simplified for radar:
     *
     * <ul>
     *   <li>Paging radius (in leaves) is derived from the avatar's
     *       {@code RadarRange} via {@link WorldGrids#LEAF_GRID} spacing — no
     *       hand-rolled {@code * 1024} arithmetic, per
     *       {@code .claude/rules/world-coordinates.md}.</li>
     *   <li>Subspace is effectively 2D (one Y layer), so the view spans only one
     *       leaf in Y — the one containing the avatar.</li>
     *   <li>Blocks are not entities — leaves come from {@link World#getLeaf} and
     *       are paged on world-grid boundaries, independent of the entity blip
     *       containers.</li>
     * </ul>
     */
    private void updateLeafPaging(final Vec3d avatarPos) {
        if (world == null || workers == null) {
            return;
        }
        final Vec3i leafSpacing = WorldGrids.LEAF_GRID.getSpacing();
        // Leaf radius rounds up so the visible disc never extends past the
        // outermost loaded leaf. With RadarRange = N world units and a leaf X-size
        // of S, ceil(N/S) leaves on each side covers the full radius.
        final int newRadius = (int) Math.ceil(currentRange / leafSpacing.x);
        final boolean radiusChanged = newRadius != radarLeafRadius;
        if (radiusChanged) {
            radarLeafRadius = newRadius;
            rebuildRadarViewArray(newRadius);
            // Force re-paging at the new radius.
            radarCenterCell.set(0, 100, 0);
        }

        final Vec3i newCenter = WorldGrids.LEAF_GRID.worldToCell(avatarPos);
        if (!radiusChanged && newCenter.equals(radarCenterCell)) {
            return;
        }
        radarCenterCell.set(newCenter);
        final Vec3i centerWorld = WorldGrids.LEAF_GRID.cellToWorld(newCenter);

        final Set<LeafId> toRemove = new HashSet<>(radarLeafCache.keySet());

        final Vec3d entryWorld = new Vec3d();
        for (final RadarViewEntry e : radarViewArray) {
            entryWorld.set(
                    centerWorld.x + e.offset.x * (double) leafSpacing.x,
                    centerWorld.y,
                    centerWorld.z + e.offset.z * (double) leafSpacing.z);
            final LeafId leafId = LeafId.fromWorld(entryWorld);
            toRemove.remove(leafId);

            RadarLeafView view = radarLeafCache.get(leafId);
            if (view == null) {
                view = new RadarLeafView(leafId);
                radarLeafCache.put(leafId, view);
                view.queued = true;
                workers.execute(view, e.priority);
            }
        }

        for (final LeafId remove : toRemove) {
            final RadarLeafView view = radarLeafCache.remove(remove);
            if (view == null) {
                continue;
            }
            view.release();
            if (view.queued && workers.cancel(view)) {
                view.queued = false;
            }
        }
    }

    private void rebuildRadarViewArray(final int radius) {
        // Only X/Z are paged — Subspace is single-leaf in Y.
        final int xzSize = 2 * radius + 1;
        radarViewArray = new RadarViewEntry[xzSize * xzSize];
        int idx = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                radarViewArray[idx++] = new RadarViewEntry(x, z);
            }
        }
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
        final float newScale = (float) (rangeWorldUnits / RADAR_CANONICAL_RANGE);
        if (newScale != blipScale) {
            blipScale = newScale;
            for (final Blip blip : blipsById.values()) {
                blip.geom.setLocalScale(blipScale);
            }
        }
    }

    private ColorRGBA colorFor(final EntityId id, final Integer entityFreq) {
        if (id.equals(avatarEntityId)) {
            return SELF_COLOR;
        }
        if (entityFreq == null) {
            return NEUTRAL_COLOR;
        }
        if (currentAvatarFreq != null && entityFreq.intValue() == currentAvatarFreq.intValue()) {
            return FRIENDLY_COLOR;
        }
        return ENEMY_COLOR;
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
            final Geometry geom = blipFactory.create(info.getShapeName(ed), NEUTRAL_COLOR);
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

    /** Pre-computed paging entry: leaf-cell offset from the avatar's leaf. */
    private static final class RadarViewEntry {
        final Vec3i offset;
        final int priority;

        RadarViewEntry(final int x, final int z) {
            // Subspace is single-leaf in Y, so y-offset is always 0.
            this.offset = new Vec3i(x, 0, z);
            // Higher priority = greater distance — workers serve closer leaves first.
            this.priority = x * x + z * z;
        }
    }

    /**
     * Async {@link Job} that loads one leaf's silhouette: fetches cell data from
     * {@link World#getLeaf} on a worker thread, builds the silhouette mesh, and
     * (back on the JME update thread) attaches the resulting node under
     * {@code radarBlockRoot}. Mirrors {@code LocalViewState.LeafView}.
     *
     * <p>The leaf node is positioned at the leaf's absolute world origin in
     * {@code initialize}-time setup, so cells render at absolute world coords —
     * the radar camera (which sits at the avatar's absolute world position) will
     * frame them correctly without any conveyor offset.
     */
    private final class RadarLeafView implements Job {

        private final LeafId leafId;
        private final Node leafNode;
        private Node attachedSilhouette;
        private Node generatedSilhouette;
        volatile boolean queued;

        RadarLeafView(final LeafId leafId) {
            this.leafId = leafId;
            this.leafNode = new Node("RadarLeaf:" + leafId);
            final Vec3i origin = leafId.getWorld(null);
            // Place the leaf at its absolute world origin; cells inside the silhouette
            // are emitted at leaf-local coordinates [0..LEAF_SIZE).
            leafNode.setLocalTranslation(origin.x, 0f, origin.z);
            radarBlockRoot.attachChild(leafNode);
        }

        @Override
        public void runOnWorker() {
            // Once a job actually starts running there's no point trying to cancel it.
            queued = false;

            final LeafData data = world.getLeaf(leafId);
            if (data == null || data.isEmpty()) {
                synchronized (this) {
                    generatedSilhouette = null;
                }
                return;
            }
            final Node temp = new Node("Silhouette:" + leafId);
            silhouetteIndex.generate(temp, data.getRawCells());
            synchronized (this) {
                generatedSilhouette = temp;
            }
        }

        @Override
        public double runOnUpdate() {
            final Node next;
            synchronized (this) {
                next = generatedSilhouette;
            }
            if (attachedSilhouette != null) {
                attachedSilhouette.removeFromParent();
                attachedSilhouette = null;
            }
            if (next != null && leafNode.getParent() != null) {
                leafNode.attachChild(next);
                attachedSilhouette = next;
                return 1;
            }
            return 0;
        }

        void release() {
            leafNode.removeFromParent();
        }
    }

    private final class RadarLeafObserver implements LeafChangeListener {
        @Override
        public void leafChanged(final LeafChangeEvent event) {
            radarUpdatedLeafIds.add(event.getLeafId());
        }
    }
}
