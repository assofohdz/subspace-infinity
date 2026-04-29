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
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.mathd.Vec3d;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.es.ship.RadarRange;

/**
 * Top-down radar HUD. Renders an offscreen orthographic view of {@link #radarRoot}
 * (the radar scene graph) into a {@link FrameBuffer}-backed texture, then composites
 * that texture onto the GUI through the shared {@code MatDefs/MiniMap/MiniMap.j3md}
 * material — same circle-mask + overlay treatment the legacy minimap used.
 *
 * <p>{@code radarRoot} is intentionally <b>detached</b> from the main scene graph and
 * carries two empty children — {@code radarEntityRoot} (entity blips, populated by
 * issue #03) and {@code radarBlockRoot} (tile silhouettes, populated by issue #04).
 * In this scaffold both stay empty, so the off-screen render produces a transparent
 * frame and only the circle outline is visible on the HUD; the visible scene appears
 * once #03 / #04 attach content.
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

    private static final int RADAR_PIXEL_SIZE = 256;
    private static final int RADAR_GUI_MARGIN_PX = 20;
    private static final float RADAR_CAM_HEIGHT = 1000f;
    private static final float RADAR_CAM_NEAR = 1f;
    private static final float RADAR_CAM_FAR = 5000f;
    private static final double DEFAULT_RANGE_WORLD_UNITS = 256.0;

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
    private double currentRange = -1.0;

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

        radarRoot = new Node("RadarRoot");
        radarEntityRoot = new Node("RadarEntityRoot");
        radarBlockRoot = new Node("RadarBlockRoot");
        radarRoot.attachChild(radarEntityRoot);
        radarRoot.attachChild(radarBlockRoot);

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
        radarViewport.setBackgroundColor(new ColorRGBA(0f, 0f, 0f, 0f));
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
        radarQuad.setLocalTranslation(
                app.getCamera().getWidth() - RADAR_PIXEL_SIZE - RADAR_GUI_MARGIN_PX,
                app.getCamera().getHeight() - RADAR_PIXEL_SIZE - RADAR_GUI_MARGIN_PX,
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
        avatarBodyPos = null;
    }

    @Override
    protected void onEnable() {
        guiNode.attachChild(radarQuad);
    }

    @Override
    protected void onDisable() {
        radarQuad.removeFromParent();
    }

    @Override
    public void update(final float tpf) {
        resolveAvatar();
        updateRangeAndPosition();

        // radarRoot is not part of rootNode/guiNode, so the engine doesn't update its
        // world transforms for us. Drive it directly so children added by #03/#04 (and
        // any per-frame mutations they do) render with up-to-date matrices.
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
        }
        if (avatarWatch == null) {
            avatarWatch = ed.watchEntity(avatarEntityId, BodyPosition.class, RadarRange.class);
            final BodyPosition bp = avatarWatch.get(BodyPosition.class);
            if (bp != null) {
                bp.initialize(avatarEntityId, 12);
                avatarBodyPos = bp;
            }
        } else if (avatarWatch.applyChanges()) {
            final BodyPosition bp = avatarWatch.get(BodyPosition.class);
            if (bp != null && bp != avatarBodyPos) {
                bp.initialize(avatarEntityId, 12);
                avatarBodyPos = bp;
            }
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
    }

    private void applyRange(final double rangeWorldUnits) {
        final float half = (float) rangeWorldUnits;
        radarCam.setFrustum(RADAR_CAM_NEAR, RADAR_CAM_FAR, -half, half, half, -half);
        currentRange = rangeWorldUnits;
    }
}
