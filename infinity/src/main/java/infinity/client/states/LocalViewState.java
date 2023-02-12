/*
 * $Id$
 *
 * Copyright (c) 2020, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.MatParamOverride;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.debug.WireBox;
import com.jme3.scene.shape.Quad;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mworld.LeafChangeEvent;
import com.simsilica.mworld.LeafChangeListener;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.LeafId;
import com.simsilica.mworld.LightData;
import com.simsilica.mworld.World;
import com.simsilica.mworld.WorldGrids;
import com.simsilica.mworld.net.client.WorldClientService;
import com.simsilica.mworld.view.FogSettings;
import com.simsilica.mworld.view.ViewMask;
import com.simsilica.state.BlackboardState;
import com.simsilica.thread.Job;
import com.simsilica.thread.JobState;
import infinity.Main;
import infinity.client.AvatarMovementState;
import infinity.client.ConnectionState;
import infinity.client.view.BlockGeometryIndex;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The player-centric view of the actual world data.
 *
 * @author Paul Speed
 */
public class LocalViewState extends BaseAppState {

  static Logger log = LoggerFactory.getLogger(LocalViewState.class);

  private Node viewRoot;

  private JobState workers;
  private JobState priorityWorkers;
  private World world;
  private VersionedReference<Vec3d> posRef;
  private final Grid leafGrid = WorldGrids.LEAF_GRID; // new Grid(32, 32, 32);
  // private Vec3i viewRadius = new Vec3i(2, 3, 2);
  private final Vec3i viewRadius = new Vec3i(2, 0, 2);
  private final Vec3i centerCell = new Vec3i(0, 100, 0); // set it to something that will never match
  private Vec3i centerWorld = new Vec3i();
  private final Vec3i maxViewRadius = new Vec3i(7, 9, 7);
  private final ViewMask viewMask = new ViewMask(WorldGrids.LEAF_GRID, maxViewRadius);
  // private Spatial maskDebugOld;
  private Spatial maskDebug;

  // Keep track of the min/max elevation for our center.  There is no reason
  // to grab leaf data from below elevation 0 or above elevation maxElevation (which
  // may be higher than the actual fractal's max because of extra build height).
  // Anyway, no reason to grab those but also no reason to give up leaf data we
  // could already see on the other end.  So we'll clamp the 'view center' such
  // that we will never query above/below 'the world'.
  private final int maxBuildHeight = 10;
  private float yMin = 0;
  private float yMax = 100;

  // Rather than run through a 3D loop all the time, we'll precalculate our
  // array as one flat array of view entries that already have that stuff calcualted.
  private ViewEntry[] viewArray;

  // Temporary box template
  private Geometry debugCellTemplate;
  private final ColorRGBA loadingColor = new ColorRGBA(0.5f, 0.5f, 0.5f, 0.5f);
  private final ColorRGBA emptyColor = new ColorRGBA(0.2f, 0.4f, 0.6f, 0.1f);
  private final ColorRGBA filledColor = new ColorRGBA(1, 1, 0, 0.5f);

  private final Map<LeafId, LeafView> viewCache = new HashMap<>();

  private final LeafObserver leafObserver = new LeafObserver();
  private BlockGeometryIndex geomIndex;

  private final ConcurrentLinkedQueue<LeafId> updatedLeafIds = new ConcurrentLinkedQueue<>();

  private FogSettings fogSettings;
  private VersionedReference<FogSettings> fogSettingsRef;

  private boolean smoothLighting = true;

  public LocalViewState() {
    setEnabled(true);
  }

  public boolean getSmoothLighting() {
    return smoothLighting;
  }

  public void setSmoothLighting(boolean b) {
    if (this.smoothLighting == b) {
      return;
    }
    this.smoothLighting = b;

    if (posRef != null) {
      // Force the cells to rebuild
      updateView(posRef.get(), true);
    }
  }

  public int getViewRadius() {
    return viewRadius.x * 32 + 32;
  }

  public void setViewRadius(int radius) {
    radius = radius - 32;
    radius = radius / 32;
    if (radius < 3) {
      radius = 3;
    } else if (radius > maxViewRadius.x) {
      radius = maxViewRadius.x;
    }
    if (radius == viewRadius.x) {
      return;
    }
    viewRadius.set(radius, radius * 4 / 3, radius);
    yMin = viewRadius.y * 32;
    yMax = maxBuildHeight - (viewRadius.y * 32) - 32; // -32 because cells grow up

    if (viewArray != null) {
      resetViewArray();
      updateView(posRef.get(), true);
    }
  }

  public ViewMask getViewMask() {
    return viewMask;
  }

  public BlockGeometryIndex getGeomIndex() {
    return geomIndex;
  }

  @Override
  protected void initialize(Application app) {

    world = getState(ConnectionState.class).getService(WorldClientService.class);

    // this.viewMask = new ViewMask(WorldGrids.LEAF_GRID, maxViewRadius);
    getState(BlackboardState.class).set(ViewMask.class, viewMask);

    //fogSettingsRef = fogSettings.createReference();

    this.viewRoot = new Node("ViewRoot");

    geomIndex = new BlockGeometryIndex(app.getAssetManager());

    // world = getState(WorldViewState.class, true).getWorld();
    workers = getState("regularWorkers", JobState.class);
    priorityWorkers = getState("priorityWorkers", JobState.class);
    posRef = getState(AvatarMovementState.class).createPositionReference();

    world.addLeafChangeListener(leafObserver);

    WireBox box = new WireBox(15.9f, 15.9f, 15.9f);
    Geometry geom = new Geometry("debugBox", box);
    geom.setMaterial(GuiGlobals.getInstance().createMaterial(loadingColor, false).getMaterial());
    geom.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
    geom.setQueueBucket(Bucket.Transparent);
    debugCellTemplate = geom;

    resetViewArray();

    // For testing our view mask
    Vec3i maskSize = viewMask.getSize();
    Quad quad;
    // quad = new Quad(viewMask.getImageWidth(), viewMask.getImageHeight());
    // geom = new Geometry("maskTest", quad);
    // geom.setMaterial(GuiGlobals.getInstance().createMaterial(viewMask.getMaskTexture(),
    // false).getMaterial());
    // maskDebugOld = geom;
    // maskDebugOld.setLocalScale(1);

    Material mat = new Material(app.getAssetManager(), "MatDefs/UnshadedArray.j3md");
    mat.setTexture("ColorMap", viewMask.getMaskTextures());

    // log.info("view map image:" + viewMask.getLayerImage(0));
    // logInfo("  ", viewMask.getLayerImage(0));

    Node debug = new Node("maskDebug");
    for (int i = 0; i < maskSize.y; i++) {
      quad = new Quad(maskSize.x, maskSize.z);
      quad.clearBuffer(VertexBuffer.Type.TexCoord);
      quad.setBuffer(
          VertexBuffer.Type.TexCoord, 3, new float[] {0, 0, i, 1, 0, i, 1, 1, i, 0, 1, i});
      // int slice = i % 2;
      // quad.setBuffer(VertexBuffer.Type.TexCoord, 3, new float[]{0, 0, slice,
      //                                                          1, 0, slice,
      //                                                          1, 1, slice,
      //                                                          0, 1, slice});
      geom = new Geometry("maskTest", quad);
      geom.setMaterial(mat);
      geom.setLocalTranslation((maskSize.x + 2) * i, 5, 0);
      debug.attachChild(geom);
    }
    debug.setLocalScale(1);
    debug.move(1, 1, 0);
    maskDebug = debug;
  }

  protected void logInfo(String indent, Texture tex) {
    log.info(indent + "key:" + tex.getKey());
    log.info(indent + "type:" + tex.getType());
    log.info(
        indent
            + "wrap s:"
            + tex.getWrap(Texture.WrapAxis.S)
            + " t:"
            + tex.getWrap(Texture.WrapAxis.T));
    // + " r:" + tex.getWrap(Texture.WrapAxis.R));
    log.info(indent + "magFilter:" + tex.getMagFilter());
    log.info(indent + "minFilter:" + tex.getMinFilter());
    log.info(indent + "name:" + tex.getName());
    log.info(indent + "shadowCompareMode:" + tex.getShadowCompareMode());
    log.info(indent + "anisotropicFilter:" + tex.getAnisotropicFilter());
    log.info(indent + "image:");
    logInfo(indent + "  ", tex.getImage());
  }

  protected void logInfo(String indent, Image img) {
    log.info(indent + "format:" + img.getFormat());
    log.info(indent + "size:" + img.getWidth() + ", " + img.getHeight() + "  x " + img.getDepth());
    log.info(indent + "hasMips:" + img.hasMipmaps());
    log.info(indent + "isGenMipsRequired:" + img.isGeneratedMipmapsRequired());
    log.info(indent + "NPOT:" + img.isNPOT());
    log.info(indent + "colorSpace:" + img.getColorSpace());
  }

  @Override
  protected void cleanup(Application app) {
    if (world != null) {
      world.removeLeafChangeListener(leafObserver);
    }
  }

  @Override
  protected void onEnable() {
    ((Main) getApplication()).getRootNode().attachChild(viewRoot);
    // ((Main)getApplication()).getGuiNode().attachChild(maskDebug);
    // ((Main)getApplication()).getGuiNode().attachChild(maskDebugOld);
    maskDebug.setLocalTranslation(0, 0, 0);
    // maskDebugOld.setLocalTranslation(0, 50, 0);
  }

  @Override
  protected void onDisable() {
    viewRoot.removeFromParent();
    // maskDebug.removeFromParent();
    // maskDebugOld.removeFromParent();
  }

  @Override
  public void update(float tpf) {
    if (posRef.update()) {
      Vec3d pos = posRef.get();
      updateView(pos, false);


      // viewRoot.setLocalTranslation(-(float)(pos.x - centerWorld.x), -64, -(float)(pos.z -
      // centerWorld.z));
      viewRoot.setLocalTranslation(
          -(float) (pos.x - centerWorld.x), 0, -(float) (pos.z - centerWorld.z));

      // For the time being, slightly move the terrain up so that it z-fights
      // less with the tiles.  Need to sort out how that will really done at some
      // point.
      // viewRoot.move(0, 0.1f, 0);
    }

    LeafId leafId = null;
    while ((leafId = updatedLeafIds.poll()) != null) {
      leafChanged(leafId);
    }
  }

  protected void resetViewArray() {
    int xSize = viewRadius.x * 2 + 1;
    int ySize = viewRadius.y * 2 + 1;
    int zSize = viewRadius.z * 2 + 1;
    viewArray = new ViewEntry[xSize * ySize * zSize];
    int index = 0;
    for (int x = -viewRadius.x; x <= viewRadius.x; x++) {
      for (int y = -viewRadius.y; y <= viewRadius.y; y++) {
        for (int z = -viewRadius.z; z <= viewRadius.z; z++) {
          viewArray[index] = new ViewEntry(x, y, z);
          index++;
        }
      }
    }
  }

  protected void updateView(Vec3d pos, boolean forceUpdate) {

    // Need to account for sea level... a mistake in the whole approach, really.
    // Vec3d realWorld = pos.add(0, 64, 0);
    // 2021-11-06 - finally trying to remove that mistake
    Vec3d realWorld = pos.add(0, 0, 0);

    // And then clamp it
    if (realWorld.y < yMin) {
      realWorld.y = yMin;
    } else if (realWorld.y > yMax) {
      realWorld.y = yMax;
    }

    Vec3i newCenter = leafGrid.worldToCell(realWorld);
    if (!forceUpdate && newCenter.equals(centerCell)) {
      return;
    }
    centerCell.set(newCenter);
    centerWorld = leafGrid.cellToWorld(centerCell);

    viewMask.setCenterWorld(centerWorld);

    log.info("Refreshing local view, centerWorld:" + centerWorld + "   pos:" + pos);

    Set<LeafId> toRemove = new HashSet<>(viewCache.keySet());

    Vec3d world = new Vec3d();
    for (ViewEntry e : viewArray) {

      world.set(centerWorld).addLocal(e.worldOffset);

      // If this leaf is below the world then we don't need to worry about it
      if (world.y < 0) {
        log.info("Skipping entry below the world:" + world);
        continue;
      }

      // GridCell leafId = leafGrid.getContainingCell(world);
      LeafId leafId = LeafId.fromWorld(world);

      // We're using this so remove it from the cleanup set.
      toRemove.remove(leafId);

      LeafView view = viewCache.get(leafId);
      if (view == null) {
        viewMask.setRendered(e.viewLoc, false);

        view = new LeafView(leafId);
        view.setSmoothLighting(smoothLighting);
        viewCache.put(leafId, view);

        view.queued = true;
        workers.execute(view, e.priority);
        // log.info("WorkerPool size:" + workers.getQueuedCount());
      } else {
        if (view.setSmoothLighting(smoothLighting)) {
          // viewMask.setRendered(e.viewLoc, view.rendered);
          // If there is already one there then we don't need to unmask
          // it but it's nice to see things actually blink when testing.
          // viewMask.setRendered(e.viewLoc, false);
          // viewMask.setRendered(e.viewLoc, view.rendered);
          view.queued = true;
          workers.execute(view, e.priority);
        } else {
          // There is already a view created but it might not be rendered yet
          viewMask.setRendered(e.viewLoc, view.rendered);
        }
      }
      view.updateOffset(e.worldOffset);

      e.leafView = view;
    }

    for (LeafId remove : toRemove) {
      LeafView view = viewCache.remove(remove);
      if (view != null) {
        view.release();

        // By checking if it's already queued we should avoid
        // book-keeping overhead trying to remove something that
        // isn't even submitted.
        if (view.queued) {
          // And cancel it if possible
          if (!workers.cancel(view)) {
            log.info("View job not canceled for:" + view.leafId);
          } else {
            view.queued = false;
          }
        }
      }
    }

    if (viewCache.size() != viewArray.length) {
      log.error(
          "*** Major data integrity error in view cache, cache size:"
              + viewCache.size()
              + "  should be:"
              + viewArray.length);
    }
  }

  protected void leafChanged(LeafId leafId) {
    log.info("leafChanged(" + leafId + ")");
    // Convert it to our grid... because they're different
    Vec3i w = leafId.getWorld(null);
    LeafView view = viewCache.get(leafId);
    log.info("view:" + view);
    if (view != null) {
      // At highest priority
      priorityWorkers.execute(view, -1);
    }
  }

  protected void updateViewMask(LeafId leafId) {
    for (ViewEntry e : viewArray) {
      if (e.leafView == null) {
        continue;
      }
      if (Objects.equals(e.leafView.leafId, leafId)) {
        viewMask.setRendered(e.viewLoc, true);
      }
    }
  }

  public Vec3i getCenterCellWorld() {
    return centerCell;
  }

  // Instead of having a 3D array where we always recalculate
  // world offsets, priorities, etc... we'll just precalculate all
  // of that stuff into one flat array
  private class ViewEntry {
    Vec3i viewLoc; // just in case we need it
    int priority; // distance from center
    Vec3d worldOffset;
    LeafView leafView;

    public ViewEntry(int x, int y, int z) {
      this.viewLoc = new Vec3i(x, y, z);
      this.priority = (x * x) + (y * y) + (z * z);
      this.worldOffset = new Vec3d(x * 32, y * 32, z * 32);
    }
  }

  private class LeafView implements Job {

    private final LeafId leafId;
    private final Node leafNode;
    private final Geometry testGeom;

    private LeafData leafData;
    private LightData lightData;
    private Node parts;
    private Node generatedParts;
    private boolean rendered = false;
    private volatile boolean queued = false;

    private boolean smoothLighting = false;

    public LeafView(LeafId leafId) {
      this.leafId = leafId;

      this.leafNode = new Node("leafNode:" + leafId);
      viewRoot.attachChild(leafNode);

      testGeom = debugCellTemplate.clone(false);
      testGeom.move(16, 16, 16);
      // leafNode.attachChild(testGeom);
    }

    public boolean setSmoothLighting(boolean b) {
      if (smoothLighting == b) {
        return false;
      }
      this.smoothLighting = b;
      this.rendered = false;
      return true;
    }

    public void updateOffset(Vec3d worldOffset) {

      leafNode.setLocalTranslation(
          (float) worldOffset.x, (float) (centerWorld.y + worldOffset.y), (float) worldOffset.z);
      // testGeom.move(16, 16, 16); // for our wire box extents
    }

    @Override
    public void runOnWorker() {

      // Once we're running there is no use trying to remove us.
      // And if we use the flag later for any type of "should I queue this"
      // check then this is also right because new changes coming in _should_
      // requeue it.
      queued = false;

      this.leafData = world.getLeaf(leafId);

      if (leafData == null){
        log.warn("No leaf data for:" + leafId);
        return;
      }

      // log.info("loaded(" + leafId + "):" + leafData);
      if (leafData.isEmpty()) {
        // log.info("Empty, nothing to do for:" + leafId);
        parts = null; // just in case we're rerun
        return;
      }

      // Vec3i world = leafId.getWorld(null); //getWorldOrigin();
      // ColumnId colId = ColumnId.fromWorld(world);
      // ColumnData column = colDb.getColumn(colId);
      // this.lightData = column.getLightData(world.y);

      // Not the most efficient way because it won't lazily load
      // the columns but it will work for now and get us to the
      // next steps
      // ColumnLightData colLightData = ColumnLightData.loadNeighborhood(colDb, column, false);
      // CellData colLightData = new ColumnNeighborhood(colDb, column).getLightingCellData();
      // CellData lightData = new OffsetCellData(colLightData, 0, world.y, 0);

      // Create a new guaranteed unconnected node for our parts
      Node temp = new Node("Parts:" + leafId);
      geomIndex.generateBlocks(temp, leafData.getRawCells());
      //geomIndex.generateBlocks(temp, leafData.getRawCells(), lightData, smoothLighting);
      synchronized (this) {
        generatedParts = temp;
      }
    }

    @Override
    public double runOnUpdate() {
      // log.info("parts(" + leafId + "):" + parts);
      if (parts != null) {
        // If we had previous parts then clear them
        parts.removeFromParent();
      }
      synchronized (this) {
        parts = generatedParts;
      }
      rendered = true;
      updateViewMask(leafId);

      if (parts == null || leafNode.getParent() == null) {
        // No real work done.
        setBoxColor(emptyColor);
        return 0;
      }
      leafNode.attachChild(parts);
      // parts.move(0, -0.25f, 0);
      setBoxColor(filledColor);
      return 1;
    }

    protected void setBoxColor(ColorRGBA color) {
      MatParamOverride override = new MatParamOverride(VarType.Vector4, "Color", color);
      leafNode.addMatParamOverride(override);
    }

    public void release() {
      // testGeom.removeFromParent();
      leafNode.removeFromParent();
    }
  }

  private class LeafObserver implements LeafChangeListener {
    @Override
    public void leafChanged(LeafChangeEvent event) {
      log.info("leafChanged(" + event + ")");
      // long leafId = event.getLeafId();
      updatedLeafIds.add(event.getLeafId());
    }
  }
}
