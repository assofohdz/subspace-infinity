// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.mathd.Vec3i;
import infinity.InfinityConstants;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.client.view.DebugFunctions;
import infinity.es.FlowFieldDebug;

/**
 * World-space overlay drawing the blended bot-steering flow around the local ship as small arrows
 * — the same flow the bots steer down, sampled server-side into {@link FlowFieldDebug} (gated by
 * {@code ZoneBotAiConfig.flowFieldDebug}). Toggle with {@link DebugFunctions#F_FLOW_FIELD}. The
 * arrows are attached <em>inside</em> {@code ModelViewState}'s {@code viewRoot} and offset by
 * {@code LocalViewState.getCenterCellWorld()} — the exact conveyor frame bodies use — so they pin to
 * world cells as the ship flies. Read-only client overlay (see {@code client-read-only.md}).
 */
public final class FlowFieldDebugState extends BaseAppState {

  // Arrow glyph dimensions in cell units (1 world unit = 1 cell): half-shaft + head barb back/half-width.
  private static final float SHAFT = 0.4f;
  private static final float HEAD_BACK = 0.22f;
  private static final float HEAD_WIDTH = 0.13f;
  private static final ColorRGBA ARROW_COLOR = new ColorRGBA(0.2f, 1f, 0.5f, 1f);

  private EntityData ed;
  private final Node arrowRoot = new Node("flow-field-debug");
  private Geometry arrows;
  private WatchedEntity flowWatch;
  private EntityId watchedShip;
  private boolean showing;
  private int originCellX;
  private int originCellZ;

  @Override
  protected void initialize(final Application app) {
    this.ed = getState(ConnectionState.class).getEntityData();
    final Material mat =
        GuiGlobals.getInstance().createMaterial(ARROW_COLOR, false).getMaterial();
    // Draw over the playfield regardless of depth so the arrows are always legible from the top-down view.
    mat.getAdditionalRenderState().setDepthTest(false);
    this.arrows = new Geometry("flow-arrows", new Mesh());
    this.arrows.setMaterial(mat);
    this.arrows.setQueueBucket(Bucket.Transparent);
    this.arrowRoot.attachChild(this.arrows);
  }

  @Override
  protected void cleanup(final Application app) {
    if (this.flowWatch != null) {
      this.flowWatch.release();
      this.flowWatch = null;
    }
  }

  @Override
  protected void onEnable() {
    GuiGlobals.getInstance().getInputMapper().addDelegate(DebugFunctions.F_FLOW_FIELD, this, "toggle");
  }

  @Override
  protected void onDisable() {
    GuiGlobals.getInstance()
        .getInputMapper()
        .removeDelegate(DebugFunctions.F_FLOW_FIELD, this, "toggle");
    this.arrowRoot.removeFromParent();
    this.showing = false;
  }

  /** Input delegate (press edge): show/hide the overlay. */
  public void toggle() {
    this.showing = !this.showing;
    if (this.showing) {
      // Attach inside the same viewRoot bodies live under, so the conveyor translation applies.
      getState(ModelViewState.class).getViewRoot().attachChild(this.arrowRoot);
    } else {
      this.arrowRoot.removeFromParent();
    }
  }

  @Override
  public void update(final float tpf) {
    if (!this.showing) {
      return;
    }
    rebindWatchIfNeeded();
    if (this.flowWatch != null && this.flowWatch.applyChanges()) {
      rebuild(this.flowWatch.get(FlowFieldDebug.class));
    }
    reposition();
  }

  /** Re-point the watch at the current ship (resolves async via RMI; null between death and respawn). */
  private void rebindWatchIfNeeded() {
    final EntityId ship = getState(GameSessionState.class).getCurrentShipId();
    if (ship == null) {
      if (this.flowWatch != null) {
        this.flowWatch.release();
        this.flowWatch = null;
        this.watchedShip = null;
      }
      return;
    }
    if (ship.equals(this.watchedShip)) {
      return;
    }
    if (this.flowWatch != null) {
      this.flowWatch.release();
    }
    this.watchedShip = ship;
    this.flowWatch = this.ed.watchEntity(ship, FlowFieldDebug.class);
    rebuild(this.flowWatch.get(FlowFieldDebug.class));
  }

  /**
   * Offset the patch by {@code -centerWorld} <em>inside</em> {@code viewRoot} (which itself carries
   * {@code -(avatarPos - centerWorld)}), so a cell renders at {@code cellWorld - avatarPos} — the
   * exact frame bodies use. {@code centerWorld} comes from {@code LocalViewState}, the same source
   * {@code ModelViewState} drives {@code viewRoot} with. Recomputed every frame because the patch
   * origin shifts each server cadence and {@code centerWorld} jumps on leaf-grid crossings.
   */
  private void reposition() {
    final Vec3i centerWorld = getState(LocalViewState.class).getCenterCellWorld();
    this.arrowRoot.setLocalTranslation(
        (float) (this.originCellX - centerWorld.x),
        (float) InfinityConstants.GAMEPLAY_Y,
        (float) (this.originCellZ - centerWorld.z));
  }

  /** Rebuild the arrow line-mesh from a freshly-synced patch (or clear it when the patch is gone). */
  private void rebuild(final FlowFieldDebug patch) {
    if (patch == null || patch.cols() == 0) {
      this.arrows.setMesh(new Mesh());
      return;
    }
    this.originCellX = patch.originCellX();
    this.originCellZ = patch.originCellZ();
    final byte[] dirs = patch.dirs();
    final int cols = patch.cols();
    int segments = 0;
    for (final byte d : dirs) {
      if (d != FlowFieldDebug.NO_FLOW) {
        segments += 3; // shaft + two head barbs
      }
    }
    final float[] pos = new float[segments * 2 * 3];
    int i = 0;
    for (int row = 0; row < patch.rows(); row++) {
      for (int col = 0; col < cols; col++) {
        final byte d = dirs[row * cols + col];
        if (d == FlowFieldDebug.NO_FLOW) {
          continue;
        }
        i = appendArrow(pos, i, col + 0.5f, row + 0.5f, FlowFieldDebug.decodeAngle(d));
      }
    }
    final Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.Lines);
    mesh.setBuffer(VertexBuffer.Type.Position, 3, pos);
    mesh.updateBound();
    this.arrows.setMesh(mesh);
  }

  /** Append a 3-segment arrow (shaft + 2 barbs) centred at {@code (cx,cz)} pointing along {@code angle}. */
  private static int appendArrow(
      final float[] pos, final int start, final float cx, final float cz, final double angle) {
    final float dx = (float) Math.cos(angle);
    final float dz = (float) Math.sin(angle);
    final float px = -dz;
    final float pz = dx;
    final float tailX = cx - dx * SHAFT;
    final float tailZ = cz - dz * SHAFT;
    final float tipX = cx + dx * SHAFT;
    final float tipZ = cz + dz * SHAFT;
    final float backX = tipX - dx * HEAD_BACK;
    final float backZ = tipZ - dz * HEAD_BACK;
    int i = start;
    i = segment(pos, i, tailX, tailZ, tipX, tipZ);
    i = segment(pos, i, tipX, tipZ, backX + px * HEAD_WIDTH, backZ + pz * HEAD_WIDTH);
    i = segment(pos, i, tipX, tipZ, backX - px * HEAD_WIDTH, backZ - pz * HEAD_WIDTH);
    return i;
  }

  /** Write one line segment (two y=0 vertices) into {@code pos}; returns the next write index. */
  private static int segment(
      final float[] pos, final int start, final float x1, final float z1, final float x2, final float z2) {
    int i = start;
    pos[i++] = x1;
    pos[i++] = 0f;
    pos[i++] = z1;
    pos[i++] = x2;
    pos[i++] = 0f;
    pos[i++] = z2;
    return i;
  }
}
