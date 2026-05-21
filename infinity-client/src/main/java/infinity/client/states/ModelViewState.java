/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
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


import com.jme3.anim.AnimComposer;
import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.simsilica.bpos.LargeGridCell;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.Filters;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.ext.mblock.BlocksResourceShapeFactory;
import com.simsilica.ext.mblock.SphereFactory;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactory;
import com.simsilica.ext.mphys.ShapeFactoryRegistry;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.lemur.core.VersionedObject;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mworld.WorldGrids;
import com.simsilica.state.BlackboardState;
import com.simsilica.state.DebugHudState;
import com.simsilica.state.DebugHudState.Location;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.es.Flag;
import infinity.es.FlagOwnership;
import infinity.es.Frequency;
import infinity.es.ShapeNames;
import infinity.es.lifecycle.CurrentShip;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages visual representations of model entities (ships, statics, lobs).
 *
 * <p>Three containers track distinct entity kinds:
 * <ul>
 *   <li>{@link BodyContainer} — entities with {@link com.simsilica.bpos.BodyPosition}
 *       + {@link com.simsilica.ext.mphys.ShapeInfo}; position driven each frame from
 *       the SimEthereal {@code BodyPosition} buffer (never poll {@code ed.getComponent}).</li>
 *   <li>{@link ModelContainer} — statics with {@link com.simsilica.ext.mphys.SpawnPosition};
 *       filter rebuilt against the {@code LEAF_GRID} cell around the avatar.</li>
 *   <li>{@link LargeModelContainer} — large statics keyed on {@link com.simsilica.bpos.LargeGridCell}
 *       (1024-unit {@code TILE_GRID}, distinct from {@code centerWorld}'s leaf grid).</li>
 * </ul>
 *
 * <p>The {@link Model} ref-count handles the same entity appearing in both
 * static and body containers (e.g. local ship has both {@code SpawnPosition}
 * and {@code BodyPosition}); body wins over static-pos updates.
 */
public class ModelViewState extends BaseAppState {

  // Package-private fields — touched by the promoted Model / Body /
  // {Body,Model,LargeModel}Container / MarkVisible siblings in this package.
  // Kept package-private rather than public so the surface stays internal to
  // this package, but no longer private since they're read across files.
  static final long VIS_DELAY = 100000000L; // 100 ms
  static Logger log = LoggerFactory.getLogger(ModelViewState.class);
  // If the block at 0, 0, 0 is the block whose own origin as
  // at 0,0,0 then it extends up to 1,1,1... So we want to make
  // sure out visualization is calibrated similarly.  Also, in
  // the test DB we generate a 'horizon' at elevation 64... which
  // is really block 63.  So a test block at 64 should extend up
  // from 64 to 65 and be sitting on the ground.
  private final List<Vector4f> testCoords = new ArrayList<>();
  private final List<Spatial> tests = new ArrayList<>();
  final Deque<MarkVisible> markerQueue = new LinkedList<>();
  // Physics grid is 32x32 but SimEthereal's grid is 64x64... which
  // means the maximum we'll see updates for is 128< away.  So for
  // a 32 grid we'd need a radius of 3... but then sometimes we'd
  // show some extra static objects.  I guess that's ok if we also
  // allow the dynamic objects to go away.
  // The grid we keep for the model interest is the same as the physics grid
  // which is different than the paged grid.
  // Though note that for the moment we require these to be the same
  // resolution.  The paged grid is necessary because it tells us how
  // to position the objects relative to the terrain.  The physics grid
  // is necessary for building the array of model filters.
  private final Vec3i largeModelCenter = new Vec3i();
  private static final int GRID_RADIUS = 2;
  final Map<EntityId, Model> modelIndex = new HashMap<>();
  private final Vector3f avatarLoc = new Vector3f();
  // Center cell
  Vec3i centerWorld = new Vec3i();
  EntityData ed;
  private SISpatialFactory siModelFactory;
  // The root node to which all managed objects will be added
  private Node viewRoot;
  TimeSource timeSource;
  private BodyContainer bodies;
  private ModelContainer models;
  private LargeModelContainer largeModels;
  private VersionedHolder<String> bodyCount;
  private VersionedHolder<String> modelCount;
  private VersionedHolder<String> largeModelCount;
  private VersionedHolder<String> spatialCount;
  EntitySet flags;
  int avatarFrequency;
  // P4 split: watch the durable player entity for CurrentShip; lazily watch
  // the current ship for Frequency. The player watch survives ship death;
  // the ship watch rebinds when the link changes. This is the structural
  // fix for the death-without-respawn NPE in the pre-P4 single-watch design.
  private WatchedEntity playerWatch;
  private WatchedEntity shipFrequencyWatch;
  private EntityId currentShipId;
  private boolean avatarInitialized;
  private LocalViewState localView;
  private VersionedReference<Vec3d> posRef;

  public ModelViewState() {
    // Nothing to do here
  }

  public Spatial getModel(final EntityId entityId) {
    Model model = modelIndex.get(entityId);
    return model == null ? null : model.spatial;
  }

  protected Spatial findPickedSpatial(final Spatial spatial) {
    Long oid = spatial.getUserData("oid");
    if (oid != null) {
      return spatial;
    }
    if (spatial.getParent() != null) {
      return findPickedSpatial(spatial.getParent());
    }
    return null;
  }

  protected void addTestObject(final Vector3f loc, final float size) {

    Vector4f coord = new Vector4f(loc.x, loc.y, loc.z, size);

    Box box = new Box(coord.w, coord.w, coord.w);
    Geometry geom = new Geometry("test", box);
    geom.setMaterial(
        com.simsilica.lemur.GuiGlobals.getInstance()
            .createMaterial(ColorRGBA.Blue, true)
            .getMaterial());
    geom.setLocalTranslation(coord.x + coord.w, coord.y + coord.w, coord.z + coord.w);
    viewRoot.attachChild(geom);

    testCoords.add(coord);
    tests.add(geom);

    resetRelativeCoordinates();
  }

  protected Node getRoot() {
    return ((SimpleApplication) getApplication()).getRootNode();
  }

  protected Node getViewRoot() {
    return viewRoot;
  }

  @Override
  protected void initialize(final Application app) {

    this.ed = getState(ConnectionState.class).getEntityData();

    this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();
    this.localView = getState(LocalViewState.class);
    this.viewRoot = new Node("objectRoot");

    // Pass the top-level Main rootNode (not viewRoot) so lights added by
    // SISpatialFactory — e.g. the ship's follower PointLight — are attached
    // to the common ancestor of *both* ModelViewState's objectRoot AND
    // LocalViewState's ViewRoot. jME lights are inherited downward only, so
    // a light on viewRoot wouldn't reach the world tiles at all.
    final EffectSpatialFactory effectFactory =
        new EffectSpatialFactory(app.getAssetManager(), this.getApplication().getTimer());
    this.siModelFactory =
        new SISpatialFactory(
            app.getAssetManager(),
            this.getApplication().getTimer(),
            localView.getGeomIndex(),
            effectFactory);

    DebugHudState debug = getState(DebugHudState.class);
    if (debug != null) {
      bodyCount = debug.createDebugValue("Bodies", Location.Right);
      modelCount = debug.createDebugValue("Statics", Location.Right);
      largeModelCount = debug.createDebugValue("Lobs", Location.Right);
      spatialCount = debug.createDebugValue("Spatials", Location.Right);
    }

    initializeFactoryRegistry();

    this.flags = ed.getEntities(Flag.class, FlagOwnership.class);

    this.bodies = new BodyContainer(this, ed);
    this.models = new ModelContainer(this, ed);
    this.largeModels = new LargeModelContainer(this, ed);

    resetModelFilter();
    resetLargeModelFilter();

    BlackboardState blackboard = getState(BlackboardState.class, true);
    posRef = ((VersionedObject<Vec3d>) blackboard.get("position")).createReference();
  }

  private void initializeFactoryRegistry() {
    ShapeFactoryRegistry<MBlockShape> shapeFactory = new ShapeFactoryRegistry<>();

    SphereFactory sphereFactory = new SphereFactory();
    shapeFactory.registerFactory(ShapeNames.SHIP_WARBIRD, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_JAVELIN, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_SHARK, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_LANCASTER, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_LEVI, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_SPIDER, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_TERRIER, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_WEASEL, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BOMBL1, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BOMBL2, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BOMBL3, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BOMBL4, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BULLETL1, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BULLETL2, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BULLETL3, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BULLETL4, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.OVER1, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.OVER2, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.OVER5, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.FLAG, sphereFactory);

    // Local cube-shape factory: client doesn't depend on server-side `CubeFactory`
    // per ADR-0005. Same one-liner as the server-side impl in infinity.sim.internal.
    ShapeFactory<MBlockShape> cubeFactory =
        (name, scale, mass) -> MBlockShape.createCube(scale);
    shapeFactory.registerFactory(ShapeNames.DOOR, cubeFactory);
    shapeFactory.registerFactory(ShapeNames.ARENA, cubeFactory);

    shapeFactory.setDefaultFactory(new BlocksResourceShapeFactory());
  }

  @Override
  protected void cleanup(final Application app) {
    DebugHudState debug = getState(DebugHudState.class);
    if (debug != null) {
      debug.removeDebugValue("Bodies");
      debug.removeDebugValue("Statics");
      debug.removeDebugValue("Lobs");
      debug.removeDebugValue("Spatials");
    }
    // Defensive container stops — BaseAppState contract permits cleanup()
    // while disabled, in which case onDisable() didn't run. Container
    // stop() is idempotent, so calling it after onDisable already did is
    // safe.
    bodies.stop();
    models.stop();
    largeModels.stop();
    flags.release();
    flags = null;
    if (playerWatch != null) {
      playerWatch.release();
      playerWatch = null;
    }
    if (shipFrequencyWatch != null) {
      shipFrequencyWatch.release();
      shipFrequencyWatch = null;
    }
    posRef = null;
  }

  @Override
  protected void onEnable() {
    getRoot().attachChild(viewRoot);
    bodies.start();
    models.start();
    largeModels.start();
  }

  @Override
  public void update(final float tpf) {
    centerWorld = localView.getCenterCellWorld();

    if (posRef.update()) {
      Vec3d pos = posRef.get();

      viewRoot.setLocalTranslation(
          -(float) (pos.x - centerWorld.x), 0, -(float) (pos.z - centerWorld.z));
      resetRelativeCoordinates();
      resetModelFilter();

      // largeModels is indexed by TILE_GRID cell (1024 units) — distinct from
      // centerWorld which tracks LEAF_GRID cells. Only rebuild the filter when
      // the player crosses a tile boundary.
      Vec3i tileCell = WorldGrids.TILE_GRID.worldToCell(pos);
      if (largeModelCenter.x != tileCell.x || largeModelCenter.z != tileCell.z) {
        largeModelCenter.x = tileCell.x;
        largeModelCenter.z = tileCell.z;
        resetLargeModelFilter();
      }
    }

    tryInitializeAvatar();

    bodies.update();
    models.update();
    largeModels.update();
    long time = timeSource.getTime();
    for (final Body body : bodies.getArray()) {
      body.update(time);
    }
    drainMarkerQueue(time);

    if (bodyCount != null) {
      bodyCount.setObject(String.valueOf(bodies.size()));
      modelCount.setObject(String.valueOf(models.size()));
      largeModelCount.setObject(String.valueOf(largeModels.size()));
      spatialCount.setObject(String.valueOf(modelIndex.size()));
    }

    syncAvatarLink();

    // If any flags change frequency, update their materials (we could be more efficient here by
    // only updating the ones that changed)
    if (flags.applyChanges()) {
      updateFlagMaterials(avatarFrequency);
    }
  }

  /** Two-tier watch maintenance: player → CurrentShip → ship Frequency. */
  private void syncAvatarLink() {
    if (playerWatch != null && playerWatch.applyChanges()) {
      rebindShipFrequencyWatch();
    }
    if (shipFrequencyWatch != null && shipFrequencyWatch.applyChanges()) {
      final Frequency f = shipFrequencyWatch.get(Frequency.class);
      if (f != null) {
        avatarFrequency = f.getFrequency();
        updateFlagMaterials(avatarFrequency);
      }
    }
  }

  private void tryInitializeAvatar() {
    if (avatarInitialized) {
      return;
    }
    final EntityId player = getState(GameSessionState.class).getPlayerEntityId();
    if (player == null) {
      return;
    }
    this.playerWatch = ed.watchEntity(player, CurrentShip.class);
    avatarInitialized = true;
    rebindShipFrequencyWatch();
  }

  /**
   * Releases the previous ship-frequency watcher (if any) and acquires a new one against the
   * current {@link CurrentShip} target. {@code null} ship = ghost state; no ship watcher and
   * the cached {@link #avatarFrequency} sticks (flag colors freeze on last-known team until
   * the next respawn binds a new ship).
   */
  private void rebindShipFrequencyWatch() {
    final CurrentShip current = playerWatch == null ? null : playerWatch.get(CurrentShip.class);
    final EntityId newShipId = current == null ? null : current.getShipId();
    if (Objects.equals(currentShipId, newShipId)) {
      return;
    }
    if (shipFrequencyWatch != null) {
      shipFrequencyWatch.release();
    }
    currentShipId = newShipId;
    if (newShipId == null) {
      shipFrequencyWatch = null;
      return;
    }
    shipFrequencyWatch = ed.watchEntity(newShipId, Frequency.class);
    final Frequency f = shipFrequencyWatch.get(Frequency.class);
    if (f != null) {
      avatarFrequency = f.getFrequency();
      updateFlagMaterials(avatarFrequency);
    }
  }

  private void drainMarkerQueue(final long time) {
    while (!markerQueue.isEmpty()) {
      // Update static model visibility
      MarkVisible marker = markerQueue.peek();
      if (marker.visibleTime > time) {
        // The earliest item in the queue is not ready yet
        return;
      }
      marker = markerQueue.poll();
      marker.update();
    }
  }

  private void updateFlagMaterials(final int shipFrequency) {
    for (final Entity flagEntity : flags) {
      updateSingleFlagMaterial(shipFrequency, flagEntity);
    }
  }

  void updateSingleFlagMaterial(final int shipFrequency, final Entity flagEntity) {
    // Race: the flags EntitySet (Flag + FlagOwnership filter) can match before
    // ModelContainer has built the entity's spatial — BodyPosition/ShapeInfo
    // sync independently from FlagOwnership. Skip and retry next frame; the
    // material flip is idempotent.
    final Spatial spatial = getModelSpatial(flagEntity.getId(), false);
    if (spatial == null) {
      return;
    }
    final FlagOwnership owner = flags.getEntity(flagEntity.getId()).get(FlagOwnership.class);
    siModelFactory.setFlagMaterialVariables(
        spatial,
        owner.freq() == shipFrequency ? Flag.FLAG_OURS : Flag.FLAG_THEIRS);
  }

  @Override
  protected void onDisable() {
    log.info("shutting down");
    bodies.stop();
    models.stop();
    largeModels.stop();
    viewRoot.removeFromParent();
  }

  protected void resetRelativeCoordinates() {
    for (int i = 0; i < tests.size(); i++) {
      Vector4f coord = testCoords.get(i);
      tests
          .get(i)
          .setLocalTranslation(
              coord.w + coord.x - centerWorld.x,
              coord.w + coord.y - centerWorld.y,
              coord.w + coord.z - centerWorld.z);
    }

    for (final Model m : models.getArray()) {
      m.updateRelativePosition();
    }
    for (final Model m : largeModels.getArray()) {
      m.updateRelativePosition();
    }
  }

  protected void resetModelFilter() {
    int size = GRID_RADIUS * 2 + 1;
    final ComponentFilter[][] gridFilters = new ComponentFilter[size][size];
    ComponentFilter[] filters = new ComponentFilter[size * size];

    int xOffset = centerWorld.x - GRID_RADIUS;
    int zOffset = centerWorld.z - GRID_RADIUS;
    int index = 0;
    for (int x = 0; x < size; x++) {
      for (int z = 0; z < size; z++) {
        long id = WorldGrids.LEAF_GRID.cellToId(xOffset + x, 0, zOffset + z);
        ComponentFilter filter = Filters.fieldEquals(SpawnPosition.class, "binId", id);
        gridFilters[x][z] = filter;
        filters[index++] = filter;
      }
    }

    models.setFilter(Filters.or(SpawnPosition.class, filters));
  }

  protected void resetLargeModelFilter() {
    // Update the large objects filter also... we'll use the same
    // radius/size for now
    int size = GRID_RADIUS * 2 + 1;
    final ComponentFilter[][] largeGridFilters = new ComponentFilter[size][size];
    ComponentFilter[] filters = new ComponentFilter[size * size];

    int xOffset = largeModelCenter.x - GRID_RADIUS;
    int zOffset = largeModelCenter.z - GRID_RADIUS;
    int index = 0;
    for (int x = 0; x < size; x++) {
      for (int z = 0; z < size; z++) {
        long id = WorldGrids.TILE_GRID.cellToId(xOffset + x, 0, zOffset + z);
        ComponentFilter filter = Filters.fieldEquals(LargeGridCell.class, "cellId", id);
        largeGridFilters[x][z] = filter;
        filters[index++] = filter;
      }
    }
    largeModels.setFilter(Filters.or(LargeGridCell.class, filters));
  }

  protected Spatial findAnimRoot(final Spatial s) {
    if (s.getControl(AnimComposer.class) != null) {
      return s;
    }
    if (s instanceof Node) {
      for (final Spatial child : ((Node) s).getChildren()) {
        Spatial result = findAnimRoot(child);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  protected Spatial createModel(final EntityId id, final ShapeInfo shapeInfo, final Mass mass) {
    final String shapeName = shapeInfo.getShapeName(ed);
    return siModelFactory.createModel(id, shapeName, mass, shapeInfo.getScale());
  }

  protected Model getModel(final EntityId entityId, final boolean create) {
    Model result = modelIndex.get(entityId);
    if (result == null && create) {
      result = new Model(this, entityId);
      modelIndex.put(entityId, result);
    }
    result.acquire();
    return result;
  }

  protected Model releaseModel(final EntityId entityId) {
    Model result = modelIndex.get(entityId);
    if (result.release()) {
      modelIndex.remove(entityId);
    }
    return result;
  }

  /** Returns the spatial for {@code entityId}; if {@code throwNotExists}, throws when missing; otherwise returns {@code null}. */
  public Spatial getModelSpatial(final EntityId entityId, final boolean throwNotExists) {
    final Model model = modelIndex.get(entityId);
    if (model == null) {
      if (throwNotExists) {
        throw new NoSuchElementException("Entity " + entityId + " does not have a spatial");
      }
      return null;
    }
    return model.spatial;
  }

  public Vector3f getAvatarLoc() {
    return avatarLoc;
  }
}
