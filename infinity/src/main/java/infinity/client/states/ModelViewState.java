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
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.ChildPositionTransition3d;
import com.simsilica.bpos.LargeGridCell;
import com.simsilica.bpos.LargeObject;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.Filters;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.ext.mblock.BlocksResourceShapeFactory;
import com.simsilica.ext.mblock.SphereFactory;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactoryRegistry;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.lemur.core.VersionedObject;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.trans.TransitionBuffer;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mworld.WorldGrids;
import com.simsilica.state.BlackboardState;
import com.simsilica.state.DebugHudState;
import com.simsilica.state.DebugHudState.Location;
import infinity.InfinityConstants;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.es.Flag;
import infinity.es.Frequency;
import infinity.es.ShapeNames;
import infinity.es.ship.Player;
import infinity.sim.CubeFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ModelViewState is responsible for managing the visual representation of the model entities.
 * It is responsible for creating and destroying the visual representations of the model entities as
 * they are created and destroyed. It also manages the visual representation of the player's own
 * ship.
 *
 * @author Asser Fahrenholz
 */
public class ModelViewState extends BaseAppState {

  private static final long VIS_DELAY = 100000000L; // 100 ms
  static Logger log = LoggerFactory.getLogger(ModelViewState.class);
  // If the block at 0, 0, 0 is the block whose own origin as
  // at 0,0,0 then it extends up to 1,1,1... So we want to make
  // sure out visualization is calibrated similarly.  Also, in
  // the test DB we generate a 'horizon' at elevation 64... which
  // is really block 63.  So a test block at 64 should extend up
  // from 64 to 65 and be sitting on the ground.
  private final List<Vector4f> testCoords = new ArrayList<>();
  private final List<Spatial> tests = new ArrayList<>();
  private final LinkedList<MarkVisible> markerQueue = new LinkedList<MarkVisible>();
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
  private final Vec3i modelCenter = new Vec3i();
  private final Vec3i largeModelCenter = new Vec3i();
  private final int gridRadius = 1; // 3;
  private final Map<EntityId, Model> modelIndex = new HashMap<>();
  // private VersionedHolder<String> attachmentCount;
  // <-----
  private final Vector3f avatarLoc = new Vector3f();
  private final Quaternion avatarRot = new Quaternion();
  // Center cell
  private Vec3i centerWorld = new Vec3i();
  private EntityData ed;
  // private WorldViewState worldView;
  private ShapeFactoryRegistry<MBlockShape> shapeFactory;
  // private BlockGeometryIndex geomIndex = new BlockGeometryIndex();
  private SISpatialFactory SImodelFactory;
  // The root node to which all managed objects will be added
  private Node viewRoot;
  private TimeSource timeSource;
  private BodyContainer bodies;
  private ModelContainer models;
  private LargeModelContainer largeModels;
  private ComponentFilter[][] gridFilters;
  private ComponentFilter[][] largeGridFilters;
  private VersionedHolder<String> bodyCount;
  private VersionedHolder<String> modelCount;
  private VersionedHolder<String> largeModelCount;
  private VersionedHolder<String> spatialCount;
  private EntitySet flags;
  private int avatarFrequency;
  private WatchedEntity avatarEntity;
  private boolean avatarInitialized = false;
  private LocalViewState localView;
  private VersionedReference<Vec3d> posRef;

  public ModelViewState() {}

  public Spatial getModel(EntityId entityId) {
    Model model = modelIndex.get(entityId);
    return model == null ? null : model.spatial;
  }

  protected Spatial findPickedSpatial(Spatial spatial) {
    Long oid = spatial.getUserData("oid");
    if (oid != null) {
      return spatial;
    }
    if (spatial.getParent() != null) {
      return findPickedSpatial(spatial.getParent());
    }
    return null;
  }

  protected void addTestObject(Vector3f loc, float size) {

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
  protected void initialize(Application app) {

    this.ed = getState(ConnectionState.class).getEntityData();

    this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();
    // this.worldView = getState(WorldViewState.class);
    this.localView = getState(LocalViewState.class);
    this.viewRoot = new Node("objectRoot");

    this.SImodelFactory =
        new SISpatialFactory(viewRoot, app.getAssetManager(), this.getApplication().getTimer(), localView.getGeomIndex());

    DebugHudState debug = getState(DebugHudState.class);
    if (debug != null) {
      bodyCount = debug.createDebugValue("Bodies", Location.Right);
      modelCount = debug.createDebugValue("Statics", Location.Right);
      largeModelCount = debug.createDebugValue("Lobs", Location.Right);
      // attachmentCount = debug.createDebugValue("Attachments", DebugHudState.Location.Right);
      spatialCount = debug.createDebugValue("Spatials", Location.Right);
    }

    initializeFactoryRegistry();

    this.flags = ed.getEntities(Flag.class, Frequency.class);

    this.bodies = new BodyContainer(ed);
    this.models = new ModelContainer(ed);
    this.largeModels = new LargeModelContainer(ed);

    resetModelFilter();

    BlackboardState blackboard = getState(BlackboardState.class, true);
    posRef = ((VersionedObject<Vec3d>) blackboard.get("position")).createReference();
  }

  private void initializeFactoryRegistry() {
    shapeFactory = new ShapeFactoryRegistry<>();


    SphereFactory sphereFactory = new SphereFactory(ed);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_WARBIRD, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_JAVELIN, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_SHARK, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_LANCASTER, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_LEVI, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_SPIDER, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_TERRIER, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_WEASEL, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BOMBL1, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BOMBL2, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BOMBL3, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BOMBL4, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BULLETL1, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BULLETL2, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BULLETL3, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BULLETL4, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.OVER1, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.OVER2, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.OVER5, 1, ed), sphereFactory);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.FLAG, 1, ed), sphereFactory);

    CubeFactory cubeFactory = new CubeFactory(ed);
    shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.DOOR, 1, ed), cubeFactory);

    shapeFactory.setDefaultFactory(new BlocksResourceShapeFactory(ed));
  }

  @Override
  protected void cleanup(Application app) {
    DebugHudState debug = getState(DebugHudState.class);
    if (debug != null) {
      debug.removeDebugValue("Bodies");
      debug.removeDebugValue("Statics");
      debug.removeDebugValue("Spatials");
    }
  }

  @Override
  protected void onEnable() {
    getRoot().attachChild(viewRoot);
    bodies.start();
    models.start();
    largeModels.start();
  }

  @Override
  public void update(float tpf) {
    centerWorld = localView.getCenterCellWorld();

    if (posRef.update()) {
      Vec3d pos = posRef.get();

      viewRoot.setLocalTranslation(
          -(float) (pos.x - centerWorld.x), 0, -(float) (pos.z - centerWorld.z));
      resetRelativeCoordinates();
    }

    if (!avatarInitialized) {
      if (getState(GameSessionState.class).getAvatarEntityId() != EntityId.NULL_ID
          || getState(GameSessionState.class).getAvatarEntityId() != null) {
        this.avatarEntity =
            ed.watchEntity(
                getState(GameSessionState.class).getAvatarEntityId(),
                Player.class,
                Frequency.class);
        if (avatarEntity != null) {
          avatarInitialized = true;
          avatarFrequency = avatarEntity.get(Frequency.class).getFrequency();
        }
      }
    }

    // log.info("update");
    // updateCenter(worldView.getViewLocation());
    bodies.update();
    models.update();
    largeModels.update();
    long time = timeSource.getTime();
    for (Body body : bodies.getArray()) {
      body.update(time);
    }
    // log.info("checking marker queue");
    while (!markerQueue.isEmpty()) {
      // Update static model visibility
      MarkVisible marker = markerQueue.peek();
      // log.info("marker visibleTime:" + marker.visibleTime + "  time:" + time);
      if (marker.visibleTime > time) {
        // The earliest item in the queue is not ready yet
        break;
      }
      marker = markerQueue.poll();
      // log.info("time:" + time + "  marker time:" + marker.visibleTime);
      marker.update();
    }

    // log.info("Body count:" + bodies.size() + "   model count:" + models.size());
    if (bodyCount != null) {
      bodyCount.setObject(String.valueOf(bodies.size()));
      modelCount.setObject(String.valueOf(models.size()));
      largeModelCount.setObject(String.valueOf(largeModels.size()));
      spatialCount.setObject(String.valueOf(modelIndex.size()));
    }

    // If our ship changes frequency, update all the flag materials
    if (avatarEntity.applyChanges()) {
      avatarFrequency = avatarEntity.get(Frequency.class).getFrequency();
      updateFlagMaterials(avatarFrequency);
    }

    // If any flags change frequency, update their materials (we could be more efficient here by
    // only updating the ones that changed)
    if (flags.applyChanges()) {
      updateFlagMaterials(avatarFrequency);
    }
  }

  private void updateFlagMaterials(int shipFrequency) {
    for (Entity flagEntity : flags) {
      updateSingleFlagMaterial(shipFrequency, flagEntity);
    }
  }

  private void updateSingleFlagMaterial(int shipFrequency, Entity flagEntity) {
    Frequency flagfrequency = flags.getEntity(flagEntity.getId()).get(Frequency.class);
    SImodelFactory.setFlagMaterialVariables(
        getModelSpatial(flagEntity.getId(), true),
        flagfrequency.getFrequency() == shipFrequency ? Flag.FLAG_OURS : Flag.FLAG_THEIRS);
  }

  @Override
  protected void onDisable() {
    log.info("shutting down");
    bodies.stop();
    models.stop();
    largeModels.stop();
    viewRoot.removeFromParent();
  }

  //  protected void updateCenter(Vector3f center) {
  //    // log.info("updateCenter(" + center + ")");
  //
  //    Vector3f cell = worldView.getViewCell();
  //
  //    // log.info("cell:" + cell + "   lastCell:" + centerCell);
  //
  //    // If the cell position has moved then we need to recalculate
  //    // relative positions of static objects
  //    if ((int) cell.x != centerCell.x
  //        || (int) cell.y != centerCell.y
  //        || (int) cell.z != centerCell.z) {
  //      centerCell.x = (int) cell.x;
  //      centerCell.y = (int) cell.y;
  //      centerCell.z = (int) cell.z;
  //      worldView.cellToWorld(centerCell, centerCellWorld);
  //      // centerCellWorld.y = 0;
  //      resetRelativeCoordinates();
  //
  //      if (modelCenter.x != centerCell.x || modelCenter.z != centerCell.z) {
  //        modelCenter.x = centerCell.x;
  //        modelCenter.z = centerCell.z;
  //        // Also need to reset the filter for the static objects
  //        resetModelFilter();
  //      }
  //
  //      // Calculate the large model center cell
  //      Vec3i largeCenter =
  //          InfinityConstants.LARGE_OBJECT_GRID.worldToCell(new Vec3d(centerCellWorld));
  //      if (largeModelCenter.x != largeCenter.x || largeModelCenter.z != largeCenter.z) {
  //        largeModelCenter.x = largeCenter.x;
  //        largeModelCenter.z = largeCenter.z;
  //        resetLargeModelFilter();
  //      }
  //    }
  //
  //    // log.info("  world:" + centerCellWorld);
  //    objectRoot.setLocalTranslation(
  //        -(center.x - centerCellWorld.x),
  //        // The camera y always == center y
  //        0, // -(center.y - centerCellWorld.y),
  //        -(center.z - centerCellWorld.z));
  //
  //    // log.info("  local:" + objectRoot.getLocalTranslation());
  //
  //    // log.info("  test:" + test.getLocalTranslation());
  //  }

  protected void resetRelativeCoordinates() {
    // log.info("********************** resetRelativeCoordinates()");
    for (int i = 0; i < tests.size(); i++) {
      Vector4f coord = testCoords.get(i);
      tests
          .get(i)
          .setLocalTranslation(
              coord.w + coord.x - centerWorld.x,
              coord.w + coord.y - centerWorld.y,
              coord.w + coord.z - centerWorld.z);
    }

    for (Model m : models.getArray()) {
      m.updateRelativePosition();
    }
    for (Model m : largeModels.getArray()) {
      m.updateRelativePosition();
    }
  }

  protected void resetModelFilter() {
    int size = gridRadius * 2 + 1;
    gridFilters = new ComponentFilter[size][size];
    // List<ComponentFilter> filters = new ArrayList<>();
    ComponentFilter[] filters = new ComponentFilter[size * size];

    // System.out.println("************************************************");
    // System.out.println("New grid center:" + modelCenter);
    int xOffset = modelCenter.x - gridRadius;
    int zOffset = modelCenter.z - gridRadius;
    int index = 0;
    for (int x = 0; x < size; x++) {
      for (int z = 0; z < size; z++) {
        long id = WorldGrids.LEAF_GRID.cellToId(xOffset + x, 0, zOffset + z);
        // System.out.print("[" + (x + xOffset) + ", " + (z + xOffset) + "=" + Long.toHexString(id)
        // + "]");
        ComponentFilter filter = Filters.fieldEquals(SpawnPosition.class, "binId", id);
        gridFilters[x][z] = filter;
        // filters.add(filter);
        filters[index++] = filter;
      }
      // System.out.println();
    }

    models.setFilter(Filters.or(SpawnPosition.class, filters));
  }

  protected void resetLargeModelFilter() {
    // Update the large objects filter also... we'll use the same
    // radius/size for now
    int size = gridRadius * 2 + 1;
    largeGridFilters = new ComponentFilter[size][size];
    ComponentFilter[] filters = new ComponentFilter[size * size];

    // System.out.println("************************************************");
    // System.out.println("New grid center:" + modelCenter);
    int xOffset = largeModelCenter.x - gridRadius;
    int zOffset = largeModelCenter.z - gridRadius;
    int index = 0;
    for (int x = 0; x < size; x++) {
      for (int z = 0; z < size; z++) {
        long id = WorldGrids.TILE_GRID.cellToId(xOffset + x, 0, zOffset + z);
        // System.out.print("[" + (x + xOffset) + ", " + (z + xOffset) + "=" + Long.toHexString(id)
        // + "]");
        ComponentFilter filter = Filters.fieldEquals(LargeGridCell.class, "cellId", id);
        largeGridFilters[x][z] = filter;
        // filters.add(filter);
        filters[index++] = filter;
      }
      // System.out.println();
    }
    largeModels.setFilter(Filters.or(LargeGridCell.class, filters));
  }

  protected Spatial findAnimRoot(Spatial s) {
    if (s.getControl(AnimComposer.class) != null) {
      return s;
    }
    if (s instanceof Node) {
      for (Spatial child : ((Node) s).getChildren()) {
        Spatial result = findAnimRoot(child);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  // protected Spatial findOldAnimRoot( Spatial s ) {
  //    if( s.getControl(AnimControl.class) != null ) {
  //        return s;
  //    }
  //    if( s instanceof Node ) {
  //        for( Spatial child : ((Node)s).getChildren() ) {
  //            Spatial result = findOldAnimRoot(child);
  //            if( result != null ) {
  //                return result;
  //            }
  //        }
  //    }
  //    return null;
  // }

  protected Spatial createModel(EntityId id, ShapeInfo shapeInfo, Mass mass) {

    String shapeName = shapeInfo.getShapeName(ed);

    // Note 03-02-2023: We're not using the shape size yet on the view-side - and that's okay for
    // now - because it
    // allows us to use the shape-size purely for the backend physics

    return SImodelFactory.createModel(id, shapeName, mass);
  }

  protected Model getModel(EntityId entityId, boolean create) {
    Model result = modelIndex.get(entityId);
    if (result == null && create) {
      result = new Model(entityId);
      modelIndex.put(entityId, result);
    }
    result.acquire();
    return result;
  }

  protected Model releaseModel(EntityId entityId) {
    Model result = modelIndex.get(entityId);
    if (result.release()) {
      modelIndex.remove(entityId);
    }
    /*
    log.info("released:" + entityId + "  remaining:" + modelIndex.keySet());
    StringBuilder sb = new StringBuilder();
    for( EntityId key : modelIndex.keySet() ) {
        if( sb.length() > 0 ) {
            sb.append(", ");
        }
        sb.append(key);
        sb.append(":");
        if( modelIndex.get(key).dynamic ) {
            sb.append("dynamic");
        } else {
            sb.append("static");
        }
    }
    log.info("states:" + sb);*/
    return result;
  }

  private void setAvatarRot(Quaternion rot) {
    this.avatarRot.set(rot);
  }

  /**
   * Returns the spatial for the specified entity. If the entity does not have a spatial and the
   * throwNotExists flag is true, a NoSuchElementException will be thrown.
   *
   * @param entityId The entity to retrieve the spatial for.
   * @param throwNotExists If true, a NoSuchElementException will be thrown if the entity does not
   * @return The spatial for the specified entity.
   */
  public Spatial getModelSpatial(final EntityId entityId, final boolean throwNotExists) {
    if (throwNotExists && !modelIndex.containsKey(entityId)) {
      throw new NoSuchElementException("Entity " + entityId + " does not have a spatial");
    }
    return modelIndex.get(entityId).spatial;
  }

  public Vector3f getAvatarLoc() {
    return avatarLoc;
  }

  private void setAvatarLoc(Vector3f newViewLoc) {
    avatarLoc.set(newViewLoc);
  }

  /**
   * Marks static models visible at a delay so that they act similar to bodies which have a strict
   * visibility time. This is to make up for the fact that SpawnPosition doesn't have a timestamp
   * and that rigid bodies will typically have both a SpawnPosition and a BodyPosition. These two
   * compete and cause the object to flicker on creation: SpawnPosition makes it visible
   * BodyPosition makes it invisible BodyPosition + delay makes it visible again. A timestamp on
   * SpawnPosition would fix this but might be overkill.
   */
  private class MarkVisible {
    Model model;
    long visibleTime;

    public MarkVisible(Model model, long visibleTime) {
      this.model = model;
      this.visibleTime = visibleTime;
    }

    public void update() {
      log.info("MarkVisible.update() useCount:" + model.useCount + "  dynamic:" + model.dynamic);
      // If the model is still static in some way then
      // we'll mark for static visibility
      if (model.useCount == 1 && model.dynamic) {
        log.info("only dynamic... should already be visible.");
        // Somehow it's only dynamic... no spawn position at all
        return;
      }
      if (model.useCount == 0) {
        // Model is no longer in use
        log.info("not used anymore");
        return;
      }

      log.info("Marking static object visible:" + model.entityId);
      // Should be safe to add our static visibility marker
      model.markVisible();
    }
  }

  /**
   * Models may be detected as static and dynamic objects at the same time because of SpawnPosition
   * and BodyPosition. Furthermore, we can't guarantee that a BodyPosition will always have a
   * corresponding SpawnPosition because it may have moved into a different zone that see see (and
   * we may not see the original spawn zone). So we need to cache them and keep track of the number
   * of 'views' using it. Also, if we already have one being managed by a Body view then we should
   * not update its static position from SpawnPosition.
   */
  private class Model {
    private final EntityId entityId;
    private Spatial spatial;
    private ShapeInfo shapeInfo;
    private int useCount;
    private boolean dynamic;
    private SpawnPosition pos;
    private int visibleCount;

    public Model(EntityId entityId) {
      this.entityId = entityId;
    }

    public void acquire() {
      useCount++;
    }

    public boolean release() {
      useCount--;
      if (useCount <= 0) {
        if (spatial != null) {
          spatial.removeFromParent();
        }
        return true;
      }
      return false;
    }

    public void setShape(ShapeInfo shapeInfo) {
      if (Objects.equals(this.shapeInfo, shapeInfo)) {
        return;
      }
      this.shapeInfo = shapeInfo;
      if (spatial != null) {
        spatial.removeFromParent();
      }
      Mass mass = ed.getComponent(entityId, Mass.class);
      spatial = createModel(entityId, shapeInfo, mass);
      if (spatial != null) {
        getViewRoot().attachChild(spatial);
        resetVisibility();
      }
    }

    public void setPosition(SpawnPosition pos) {
      this.pos = pos;
      updateRelativePosition();
    }

    public void updateRelativePosition() {
      // log.info("updateRelativePosition entityId:" + entityId + "  dynamic:" + dynamic);
      if (!dynamic) {
        if (pos == null) {
          // We are not a static model and we are probably being removed
          log.info("dynamic=false, pos=null, useCount=" + useCount);
        } else {
          Vector3f loc = pos.getLocation().toVector3f();

          // Make the position relative to our "conveyor"
          loc.subtractLocal(centerWorld.toVector3f());

          spatial.setLocalTranslation(loc);
          log.info("updateRelPos(" + entityId + "):" + loc);
          spatial.setLocalRotation(pos.getOrientation().toQuaternion());
        }
      }
    }

    public void setDynamic(boolean dynamic) {
      if (this.dynamic == dynamic) {
        return;
      }
      this.dynamic = dynamic;
      if (!dynamic) {
        updateRelativePosition();
      }
    }

    protected void markVisible() {
      visibleCount++;
      resetVisibility();
    }

    protected void markInvisible() {
      visibleCount--;
      resetVisibility();
    }

    protected void resetVisibility() {
      log.info("resetVisibility():" + visibleCount);
      if (visibleCount > 0) {
        log.info("visible:" + entityId);
        spatial.setCullHint(Spatial.CullHint.Inherit);
      } else {
        log.info("invisible:" + entityId);
        spatial.setCullHint(Spatial.CullHint.Always);
      }
    }
  }

  private class Body {
    private final Entity entity;
    // private ShapeInfo shapeInfo;
    // private Spatial model;
    private final Model model;
    boolean visible;
    boolean forceInvisible; // just in case
    // private Vector3f centerCellWorld = new Vector3f();
    private BodyPosition pos;
    private TransitionBuffer<ChildPositionTransition3d> buffer;
    private EntityId lastParent;

    public Body(Entity entity) {
      this.entity = entity;
      this.model = getModel(entity.getId(), true);
      model.setDynamic(true);
    }

    // public void setCenterCellWorld( Vector3f centerCellWorld ) {
    //    this.centerCellWorld.set(centerCellWorld);
    // }

    public void setShape(ShapeInfo shapeInfo) {
      model.setShape(shapeInfo);
      /*if( model.spatial != null ) {
          resetVisibility();
      }*/
    }

    public void setPosition(BodyPosition pos) {
      if (this.pos == pos) {
        return;
      }

      // BodyPosition requires special management to make
      // sure all instances of BodyPosition are sharing the same
      // thread-safe history buffer.  Everywhere it's used, it should
      // be 'initialized'.
      pos.initialize(entity.getId(), 12);
      this.buffer = pos.getBuffer();
    }

    public void update(long time) {

      // Look back in the brief history that we've kept and
      // pull an interpolated value.  To do this, we grab the
      // span of time that contains the time we want.  PositionTransition3d
      // represents a starting and an ending pos+rot over a span of time.
      ChildPositionTransition3d trans = buffer.getTransition(time);
      if (trans != null) {
        Vector3f pos = trans.getPosition(time, true).toVector3f();
        // if( entity.getId().getId() == 7 || entity.getId().getId() == 6 ) {
        //    log.info("object root:" + getObjectRoot() + " parent:" + model.spatial.getParent());
        // }
        Quaternion rot = trans.getRotation(time, true).toQuaternion();

        if (model.spatial.getParent() == getViewRoot()) {
          // Make the position relative to our "conveyor"
          pos.subtractLocal(centerWorld.toVector3f());
        }
        // log.info("pos: "+pos.toString());
        // log.info("body.update(" + entity.getId() + "):" + pos);
        model.spatial.setLocalTranslation(pos);
        model.spatial.setLocalRotation(rot);

        setVisible(trans.getVisibility(time));

        /*
                        if (trans.getVisibility(time)){
                            if (entity.getId().getId() == avatarEntityId.getId()){

        //log.info("Body[" + entity.getId() + "] position:" + model.spatial.getLocalTranslation());
        //if( entity.getId().getId() == 7 || entity.getId().getId() == 6 ) {
        log.info("Body[" + entity.getId() + "] world position:" +
        model.spatial.getWorldTranslation()+", centerCellWorld = "+centerCellWorld);
        //    log.info("**** setVisible(" + trans.getVisibility(time) + ")");
        //}

                avatarEnabled = true;
                //We need the world location to make sure we move the view accordingly
                setAvatarLoc(model.spatial.getWorldTranslation());
            }
        }
        */
        // See if it's connected to a parent
        EntityId parentId = trans.getParentId(time, true);

        // if( entity.getId().getId() == 7 || entity.getId().getId() == 6 ) {
        //    log.info("parent:" + parentId);
        // }
        if (!Objects.equals(parentId, lastParent)) {
          // Now make the parent right
          if (parentId == null) {
            getViewRoot().attachChild(model.spatial);
            lastParent = parentId;
          } else {
            // See if we have a parent model already
            // We look it up directly so that it doesn't
            // trigger an "acquire".  We don't want to add to
            // the usage count as that's meant for managing bodies
            // versus statics.
            Model parent = modelIndex.get(parentId);
            if (parent != null) {
              ((Node) parent.spatial).attachChild(model.spatial);
            }
            lastParent = parentId;
          }
        }
      }
    }

    protected void setVisible(boolean f) {
      // log.info("setVisible(" + entity.getId() + ", " + f + ")");
      if (this.visible == f) {
        return;
      }
      // For now, ignore setting false
      // if( !f ) return;
      this.visible = f;
      // resetVisibility();
      if (visible) {
        model.markVisible();
      } else {
        model.markInvisible();
      }
    }

    /*        protected void resetVisibility() {
                //if( model.useCount > 1 || (visible && !forceInvisible) ) {
                if( (visible && !forceInvisible) ) {
    log.info("Setting visible:" + entity.getId());
                    //model.spatial.setCullHint(Spatial.CullHint.Inherit);
                    model.markVisible();
                } else {
    log.info("Setting NOT visible:" + entity.getId());
                    //model.spatial.setCullHint(Spatial.CullHint.Always);
                    model.markInvisible();
                }
            }*/

    public void release() {
      releaseModel(entity.getId());
      model.setDynamic(false);
      // model.release();
      /*if( model != null ) {
          model.removeFromParent();
      }*/
    }
  }

  private class BodyContainer extends EntityContainer<Body> {
    public BodyContainer(EntityData ed) {
      // Because at least in this demo, shape and model are the same thing
      super(ed, BodyPosition.class, ShapeInfo.class);
    }

    public Body[] getArray() {
      return super.getArray();
    }

    protected Body addObject(Entity e) {
      log.info("add body for:" + e.getId());
      Body object = new Body(e);
      updateObject(object, e);
      return object;
    }

    protected void updateObject(Body object, Entity e) {
      object.setShape(e.get(ShapeInfo.class));
      object.setPosition(e.get(BodyPosition.class));
    }

    protected void removeObject(Body object, Entity e) {
      log.info("remove body for:" + e.getId());
      object.release();
    }
  }

  /** Keeps track of the static models in the scene. */
  private class ModelContainer extends EntityContainer<Model> {
    public ModelContainer(EntityData ed) {
      super(ed, SpawnPosition.class, ShapeInfo.class);
    }

    public void setFilter(ComponentFilter filter) {
      super.setFilter(filter);
    }

    public Model[] getArray() {
      return super.getArray();
    }

    protected Model addObject(Entity e) {
      log.info("add model for:" + e.getId() + "   at time:" + timeSource.getTime());
      Model object = getModel(e.getId(), true);
      updateObject(object, e);

      // Add it to the queue to be made visible at a future time
      markerQueue.add(new MarkVisible(object, timeSource.getTime() + VIS_DELAY));

      return object;
    }

    protected void updateObject(Model object, Entity e) {
      object.setShape(e.get(ShapeInfo.class));
      object.setPosition(e.get(SpawnPosition.class));

      // Check if the entity is a flag and if so, update the flagmaterials
      if (flags.containsId(e.getId())) {
        updateSingleFlagMaterial(avatarFrequency, e);
      }
    }

    protected void removeObject(Model object, Entity e) {
      log.info("remove model for:" + e.getId());
      releaseModel(e.getId());
    }
  }

  /** Keeps track of the large static models in the scene. */
  private class LargeModelContainer extends EntityContainer<Model> {
    public LargeModelContainer(EntityData ed) {
      super(ed, SpawnPosition.class, ShapeInfo.class, LargeObject.class, LargeGridCell.class);
    }

    public void setFilter(ComponentFilter filter) {
      super.setFilter(filter);
    }

    public Model[] getArray() {
      return super.getArray();
    }

    protected Model addObject(Entity e) {
      log.info("LargeObject add model for:" + e.getId() + "   at time:" + timeSource.getTime());
      Model object = getModel(e.getId(), true);
      updateObject(object, e);

      // Add it to the queue to be made visible at a future time
      markerQueue.add(new MarkVisible(object, timeSource.getTime() + VIS_DELAY));

      return object;
    }

    protected void updateObject(Model object, Entity e) {
      object.setShape(e.get(ShapeInfo.class));
      object.setPosition(e.get(SpawnPosition.class));
    }

    protected void removeObject(Model object, Entity e) {
      log.info("LargeObject remove model for:" + e.getId());
      releaseModel(e.getId());
    }
  }
}
