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
package infinity.client.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.Filters;
import com.simsilica.es.WatchedEntity;
import com.simsilica.es.common.Decay;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.ext.mblock.BlocksResourceShapeFactory;
import com.simsilica.ext.mblock.SphereFactory;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactoryRegistry;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.trans.PositionTransition3d;
import com.simsilica.mathd.trans.TransitionBuffer;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.state.DebugHudState;

import infinity.InfinityConstants;
import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;
import infinity.es.BodyPosition;
import infinity.es.LargeGridCell;
import infinity.es.LargeObject;
import infinity.es.PointLightComponent;
import infinity.es.ShapeNames;
import infinity.es.TileType;

/**
 *
 *
 * @author Paul Speed
 */
public class ModelViewState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(ModelViewState.class);

    private static final long VIS_DELAY = 100000000L; // 100 ms

    private EntityData ed;
    private WorldViewState worldView;

    int spatialMovedTwiceInOneFrameCount = 0;

    private Grid grid;

    private ShapeFactoryRegistry<MBlockShape> shapeFactory;
    // private BlockGeometryIndex geomIndex = new BlockGeometryIndex();
    private SpatialFactory modelFactory;
    private SISpatialFactory siModelFactory;

    // The root node to which all managed objects will be added
    private Node objectRoot;

    // Center cell
    private Vec3i centerCell = new Vec3i();
    private Vector3f centerCellWorld = new Vector3f();

    // If the block at 0, 0, 0 is the block whose own origin as
    // at 0,0,0 then it extends up to 1,1,1... So we want to make
    // sure out visualization is calibrated similarly. Also, in
    // the test DB we generate a 'horizon' at elevation 64... which
    // is really block 63. So a test block at 64 should extend up
    // from 64 to 65 and be sitting on the ground.
    /*
     * private float[][] testCoords = { {0, 64, 0}, {0, 0, 0}, {32, 64, 32} };
     * private Spatial[] tests = new Spatial[testCoords.length];
     */
    private List<Vector4f> testCoords = new ArrayList<>();
    private WatchedEntity watchedAvatar;
    private GameSessionClientService gameSession;
    private Spatial avatarSpatial;
    private TransitionBuffer avatarBuffer;
    private Vector3f avatarPos;

    {
        testCoords.add(new Vector4f(0, 64, 0, 0.5f));
        testCoords.add(new Vector4f(0, 0, 0, 0.5f));
        testCoords.add(new Vector4f(32, 64, 32, 0.5f));
    }
    private List<Spatial> tests = new ArrayList<>();

    private TimeSource timeSource;
    private MobContainer mobs;
    private ModelContainer models;
    private LargeModelContainer largeModels;

    private LinkedList<MarkVisible> markerQueue = new LinkedList<MarkVisible>();

    // Physics grid is 32x32 but SimEthereal's grid is 64x64... which
    // means the maximum we'll see updates for is 128< away. So for
    // a 32 grid we'd need a radius of 3... but then sometimes we'd
    // show some extra static objects. I guess that's ok if we also
    // allow the dynamic objects to go away.
    // The grid we keep for the model interest is the same as the physics grid
    // which is different than the paged grid.
    // Though note that for the moment we require these to be the same
    // resolution. The paged grid is necessary because it tells us how
    // to position the objects relative to the terrain. The physics grid
    // is necessary for building the array of model filters.
    private Vec3i modelCenter = new Vec3i();
    private Vec3i largeModelCenter = new Vec3i();
    private int gridRadius = 1; // 3;
    private ComponentFilter[][] gridFilters;

    private ComponentFilter[][] largeGridFilters;

    private Map<EntityId, Model> modelIndex = new HashMap<>();

    private VersionedHolder<String> mobCount;
    private VersionedHolder<String> modelCount;
    private VersionedHolder<String> largeModelCount;
    private VersionedHolder<String> spatialCount;

    private EntitySet tileTypes;
    private Map<EntityId, Spatial> spatialIndex = new HashMap<>();

    // Lights-->
    private EntitySet movingPointLights, decayingPointLights;
    private HashMap<EntityId, PointLight> pointLightMap = new HashMap<>();
    private Vec3d pointLightOffset = new Vec3d(0, 5, 0);

    // <<--Lights
    public ModelViewState() {
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

    public PickedObject pickObject() {

        // I think right now we only care about the view.y in view space
        Vector3f viewLoc = worldView.getViewLocation();
        Vector3f view = new Vector3f(viewLoc);
        view.x = 0;
        view.z = 0;

        // ...cheat for now
        Vector3f dir = getApplication().getCamera().getDirection();

        Ray ray = new Ray(view, dir);
        ray.setLimit(10);

        log.info("pickObject()------ center cell world:" + centerCellWorld + "  viewLoc:" + viewLoc);

        CollisionResults crs = new CollisionResults();
        int count = objectRoot.collideWith(ray, crs);
        log.info("pickObject() count:" + count);
        for (CollisionResult cr : crs) {
            log.info("pickObject() cr:" + cr);
            Spatial picked = findPickedSpatial(cr.getGeometry());
            log.info("pickObject()  picked:" + picked);
            if (picked != null) {
                Long oid = picked.getUserData("oid");
                EntityId entityId = new EntityId(oid);

                Vector3f cp = cr.getContactPoint();

                log.info("pickObject() cp:" + cp);

                // We will certainly have to change this once we sort out
                // the view loc versus world loc, etc. properly
                Vector3f loc = cp.add(viewLoc);
                loc.y = cp.y;

                // Just for testing the location
                addTestObject(loc, 0.01f);

                return new PickedObject(entityId, new Vec3d(loc));
            }
        }

        return null;
    }

    protected void addTestObject(Vector3f loc, float size) {

        Vector4f coord = new Vector4f(loc.x, loc.y, loc.z, size);

        Box box = new Box(coord.w, coord.w, coord.w);
        Geometry geom = new Geometry("test", box);
        geom.setMaterial(
                com.simsilica.lemur.GuiGlobals.getInstance().createMaterial(ColorRGBA.Blue, true).getMaterial());
        geom.setLocalTranslation(coord.x + coord.w, coord.y + coord.w, coord.z + coord.w);
        objectRoot.attachChild(geom);

        testCoords.add(coord);
        tests.add(geom);

        resetRelativeCoordinates();
    }

    protected Node getRoot() {
        return ((SimpleApplication) getApplication()).getRootNode();
    }

    protected Node getObjectRoot() {
        return objectRoot;
    }

    @Override
    protected void initialize(Application app) {
        this.ed = getState(ConnectionState.class).getEntityData();
        this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();
        this.worldView = getState(WorldViewState.class);
        this.objectRoot = new Node("objectRoot");
        this.grid = InfinityConstants.PHYSICS_GRID;

        DebugHudState debug = getState(DebugHudState.class);
        if (debug != null) {
            mobCount = debug.createDebugValue("Mobs", DebugHudState.Location.Right);
            modelCount = debug.createDebugValue("Statics", DebugHudState.Location.Right);
            largeModelCount = debug.createDebugValue("Lobs", DebugHudState.Location.Right);
            spatialCount = debug.createDebugValue("Spatials", DebugHudState.Location.Right);
        }

        // this.shapeFactory =
        // (ShapeFactory<MBlockShape>)getState(GameSystemsState.class).get(ShapeFactory.class);
        this.shapeFactory = new ShapeFactoryRegistry<>();
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.SHIP_WARBIRD, 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BOMBL1, 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BOMBL2, 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BOMBL3, 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BOMBL4, 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BULLETL1, 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BULLETL2, 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BULLETL3, 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create(ShapeNames.BULLETL4, 1, ed), new SphereFactory());
        shapeFactory.setDefaultFactory(new BlocksResourceShapeFactory(ed));

        this.modelFactory = new SpatialFactory(ed, ((SimpleApplication) app).getRootNode(), app.getAssetManager());

        // Some test objects
        // for( int i = 0; i < tests.length; i++ ) {
        /*
         * for (Vector4f coord : testCoords) { //float[] coord = testCoords[i]; Box box
         * = new Box(coord.w, coord.w, coord.w); Geometry geom = new Geometry("test",
         * box);
         * geom.setMaterial(com.simsilica.lemur.GuiGlobals.getInstance().createMaterial(
         * ColorRGBA.Blue, true).getMaterial()); geom.setLocalTranslation(coord.x +
         * coord.w, coord.y + coord.w, coord.z + coord.w); objectRoot.attachChild(geom);
         *
         * //tests[i] = geom; tests.add(geom); }
         */
        this.mobs = new MobContainer(ed);
        this.models = new ModelContainer(ed);
        this.largeModels = new LargeModelContainer(ed);

        resetModelFilter();

        this.tileTypes = ed.getEntities(TileType.class);

        this.movingPointLights = ed.getEntities(PointLightComponent.class, BodyPosition.class); // Moving point lights
        this.decayingPointLights = ed.getEntities(PointLightComponent.class, Decay.class); // Lights that decay
    }

    @Override
    protected void cleanup(Application app) {
        DebugHudState debug = getState(DebugHudState.class);
        if (debug != null) {
            debug.removeDebugValue("Mobs");
            debug.removeDebugValue("Statics");
            debug.removeDebugValue("Spatials");
        }
        tileTypes.release();
        tileTypes = null;

        this.movingPointLights.release();
        this.movingPointLights = null;
    }

    @Override
    protected void onEnable() {
        getRoot().attachChild(objectRoot);
        mobs.start();
        models.start();
        largeModels.start();
        this.gameSession = getState(ConnectionState.class).getService(GameSessionClientService.class);
    }

    @Override
    public void update(float tpf) {

        watchedAvatar.applyChanges();

        decayingPointLights.applyChanges();
        movingPointLights.applyChanges();

        tileTypes.applyChanges();
//log.info("update");
        updateCenter(worldView.getViewLocation());
        mobs.update();
        models.update();
        largeModels.update();
        long time = timeSource.getTime();
        for (Mob mob : mobs.getArray()) {
            mob.update(time);
        }
//log.info("checking marker queue");
        while (!markerQueue.isEmpty()) {
            // Update static model visibility
            MarkVisible marker = markerQueue.peek();
//log.info("marker visibleTime:" + marker.visibleTime + "  time:" + time);
            if (marker.visibleTime > time) {
                // The earliest item in the queue is not ready yet
                break;
            }
            marker = markerQueue.poll();
//log.info("time:" + time + "  marker time:" + marker.visibleTime);
            marker.update();
        }

//log.info("Mob count:" + mobs.size() + "   model count:" + models.size());
        if (mobCount != null) {
            mobCount.setObject(String.valueOf(mobs.size()));
            modelCount.setObject(String.valueOf(models.size()));
            largeModelCount.setObject(String.valueOf(largeModels.size()));
            spatialCount.setObject(String.valueOf(modelIndex.size()));
        }

    }

    @Override
    protected void onDisable() {
        log.info("shutting down");
        mobs.stop();
        models.stop();
        largeModels.stop();
        objectRoot.removeFromParent();
    }

    protected void updateCenter(Vector3f center) {
        // log.info("updateCenter(" + center + ")");

        Vector3f cell = worldView.getViewCell();

        // If the cell position has moved then we need to recalculate
        // relative positions of static objects
        if ((int) cell.x != centerCell.x || (int) cell.y != centerCell.y || (int) cell.z != centerCell.z) {
            log.info("cell:" + cell + "   lastCell:" + centerCell);

            centerCell.x = (int) cell.x;
            centerCell.y = (int) cell.y;
            centerCell.z = (int) cell.z;
            worldView.cellToWorld(centerCell, centerCellWorld);
            centerCellWorld.y = 0;
            resetRelativeCoordinates();

            if (modelCenter.x != centerCell.x || modelCenter.z != centerCell.z) {
                modelCenter.x = centerCell.x;
                modelCenter.z = centerCell.z;
                // Also need to reset the filter for the static objects
                resetModelFilter();
            }

            // Calculate the large model center cell
            Vec3i largeCenter = InfinityConstants.LARGE_OBJECT_GRID.worldToCell(new Vec3d(centerCellWorld));
            if (largeModelCenter.x != largeCenter.x || largeModelCenter.z != largeCenter.z) {
                largeModelCenter.x = largeCenter.x;
                largeModelCenter.z = largeCenter.z;
                resetLargeModelFilter();
            }
        }

        // log.info(" centerCellWorld:" + centerCellWorld);
        objectRoot.setLocalTranslation(-(center.x - centerCellWorld.x),
                // The camera y always == center y
                0, // -(center.y - centerCellWorld.y),
                -(center.z - centerCellWorld.z));

        // log.info(" objectRootWorld:" + objectRoot.getWorldTranslation());
        // log.info(" objectRootLocal:" + objectRoot.getLocalTranslation());
        // log.info(" test:" + test.getLocalTranslation());
    }

    protected void resetRelativeCoordinates() {
        log.info("********************** resetRelativeCoordinates()");
        for (int i = 0; i < tests.size(); i++) {
            Vector4f coord = testCoords.get(i);
            tests.get(i).setLocalTranslation(coord.w + coord.x - centerCellWorld.x,
                    coord.w + coord.y - centerCellWorld.y, coord.w + coord.z - centerCellWorld.z);
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

//System.out.println("************************************************");
//System.out.println("New grid center:" + modelCenter);
        int xOffset = modelCenter.x - gridRadius;
        int zOffset = modelCenter.z - gridRadius;
        int index = 0;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                long id = InfinityConstants.PHYSICS_GRID.cellToId(xOffset + x, 0, zOffset + z);
//System.out.print("[" + (x + xOffset) + ", " + (z + xOffset) + "=" + Long.toHexString(id) + "]");
                ComponentFilter filter = Filters.fieldEquals(SpawnPosition.class, "binId", id);
                gridFilters[x][z] = filter;
                // filters.add(filter);
                filters[index++] = filter;
            }
//System.out.println();
        }
        log.info("Setting static model filter to: " + Arrays.toString(filters));
        models.setFilter(Filters.or(SpawnPosition.class, filters));
    }

    protected void resetLargeModelFilter() {
        // Update the large objects filter also... we'll use the same
        // radius/size for now
        int size = gridRadius * 2 + 1;
        largeGridFilters = new ComponentFilter[size][size];
        ComponentFilter[] filters = new ComponentFilter[size * size];

//System.out.println("************************************************");
//System.out.println("New grid center:" + modelCenter);
        int xOffset = largeModelCenter.x - gridRadius;
        int zOffset = largeModelCenter.z - gridRadius;
        int index = 0;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                long id = InfinityConstants.LARGE_OBJECT_GRID.cellToId(xOffset + x, 0, zOffset + z);
//System.out.print("[" + (x + xOffset) + ", " + (z + xOffset) + "=" + Long.toHexString(id) + "]");
                ComponentFilter filter = Filters.fieldEquals(LargeGridCell.class, "cellId", id);
                largeGridFilters[x][z] = filter;
                // filters.add(filter);
                filters[index++] = filter;
            }
//System.out.println();
        }
        largeModels.setFilter(Filters.or(LargeGridCell.class, filters));

    }

    protected Spatial createModel(EntityId id, MBlockShape blockShape, ShapeInfo shapeInfo, Mass mass) {
        return modelFactory.createModel(id, blockShape, shapeInfo, mass);
        /*
         * Spatial spatial; CellArray cells = shape.getCells(); if( cells != null ) {
         * Node node = new Node("Object:" + id); Node parts = new Node("Parts:" + id);
         * node.attachChild(parts); spatial = node;
         *
         * geomIndex.generateBlocks(parts, cells);
         *
         * // For the time being, we'll just consider the CoG to be the center // of the
         * model. //Vector3f cogOffset = cells.getSize().toVector3f();
         * //cogOffset.multLocal((float)(-0.5 * shape.getScale()));
         *
         * BodyMass bm = shape.getMass();
         *
         * // The position of the object is its CoG... which means // we need to offset
         * our model's origin by it. It should // already be scaled and everything...
         * just need to negate it. Vector3f cogOffset =
         * bm.getCog().toVector3f().negate();
         *
         * // We need to sort out what the center should be. Directly out of
         * generateBlocks() // the geometry is all relative to the corner. See
         * cog-offset.txt parts.move(cogOffset);
         * parts.setLocalScale((float)shape.getScale());
         * parts.setShadowMode(ShadowMode.CastAndReceive);
         *
         * } else { float radius = (float)shape.getMass().getRadius(); Sphere mesh = new
         * Sphere(24, 24, radius); mesh.setTextureMode(Sphere.TextureMode.Projected);
         * mesh.scaleTextureCoordinates(new Vector2f(4, 2)); Geometry geom = new
         * Geometry("Object:" + id, mesh); spatial = geom;
         *
         * if( mass != null && mass.getMass() != 0 ) {
         * geom.setMaterial(GuiGlobals.getInstance().createMaterial(new ColorRGBA(0,
         * 0.6f, 0.6f, 1), true).getMaterial());
         *
         * Texture texture =
         * GuiGlobals.getInstance().loadTexture("Interface/grid-shaded-labeled.png",
         * true, true); geom.getMaterial().setTexture("DiffuseMap", texture); } else {
         * // Just a flat green
         * geom.setMaterial(GuiGlobals.getInstance().createMaterial(new ColorRGBA(0.2f,
         * 0.6f, 0.2f, 1), true).getMaterial()); }
         *
         * spatial.setShadowMode(ShadowMode.CastAndReceive); }
         *
         * spatial.setUserData("oid", id.getId());
         *
         * return spatial;
         */
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
         * log.info("released:" + entityId + "  remaining:" + modelIndex.keySet());
         * StringBuilder sb = new StringBuilder(); for( EntityId key :
         * modelIndex.keySet() ) { if( sb.length() > 0 ) { sb.append(", "); }
         * sb.append(key); sb.append(":"); if( modelIndex.get(key).dynamic ) {
         * sb.append("dynamic"); } else { sb.append("static"); } } log.info("states:" +
         * sb);
         */
        return result;
    }

    public void setAvatar(EntityId avatarId) {
        watchedAvatar = ed.watchEntity(avatarId, BodyPosition.class);
    }

    /**
     * Marks static models visible at a delay so that they act similar to mobs which
     * have a strict visibility time. This is to make up for the fact that
     * SpawnPosition doesn't have a timestamp and that rigid bodies will typically
     * have both a SpawnPosition and a BodyPosition. These two compete and cause the
     * object to flicker on creation: SpawnPosition makes it visible BodyPosition
     * makes it invisible BodyPosition + delay makes it visible again. A timestamp
     * on SpawnPosition would fix this but might be overkill.
     */
    private class MarkVisible {

        Model model;
        long visibleTime;

        public MarkVisible(Model model, long visibleTime) {
            this.model = model;
            this.visibleTime = visibleTime;
        }

        public void update() {
            // log.info("MarkVisible.update() useCount:" + model.useCount + " dynamic:" +
            // model.dynamic);
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

            // log.info("Marking static object visible:" + model.entityId);
            // Should be safe to add our static visibility marker
            model.markVisible();
        }
    }

    /**
     * Models may be detected as static and dynamic objects at the same time because
     * of SpawnPosition and BodyPosition. Furthermore, we can't guarantee that a
     * BodyPosition will always have a corresponding SpawnPosition because it may
     * have moved into a different zone that see see (and we may not see the
     * original spawn zone). So we need to cache them and keep track of the number
     * of 'views' using it. Also, if we already have one being managed by a Mob view
     * then we should not update its static position from SpawnPosition.
     */
    private class Model {

        private EntityId entityId;
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
            spatial = createModel(entityId, shapeFactory.createShape(shapeInfo, mass), shapeInfo, mass);
            if (spatial != null) {

                // if (getAvatarSpatial() == null && entityId.getId() ==
                // watchedAvatar.getId().getId()) {
                // setAvatarSpatial(spatial);
                // Node playerNode = new Node();
                // playerNode.attachChild(spatial);
                // } else {
                // getObjectRoot().attachChild(spatial);
                // }
                getObjectRoot().attachChild(spatial);

                resetVisibility();

                if (spatial.getUserDataKeys().contains("arena")) {
                    this.markInvisible();
                }
            }
        }

        public void setPosition(SpawnPosition pos) {
            this.pos = pos;
            updateRelativePosition();
        }

        public void updateRelativePosition() {
//log.info("updateRelativePosition entityId:" + entityId + "  dynamic:" + dynamic);
            if (!dynamic) {
                if (pos == null) {
                    // We are not a static model and we are probably being removed
                    log.info("dynamic=false, pos=null, useCount=" + useCount);
                } else {
                    Vector3f loc = pos.getLocation().toVector3f();

                    // Make the position relative to our "conveyor"
                    log.info("Updating relative position for model from " + loc);
                    loc.subtractLocal(centerCellWorld);
                    log.info("-- to " + loc);

                    spatial.setLocalTranslation(loc);
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
//log.info("resetVisibility():" + visibleCount);
            if (visibleCount > 0) {
//log.info("visible:" + entityId);
                spatial.setCullHint(Spatial.CullHint.Inherit);
            } else {
//log.info("invisible:" + entityId);
                spatial.setCullHint(Spatial.CullHint.Always);
            }
        }
    }

    private class Mob {

        private Entity entity;
        // private ShapeInfo shapeInfo;
        // private Spatial model;
        private Model model;

        // private Vector3f centerCellWorld = new Vector3f();
        private BodyPosition pos;
        private TransitionBuffer<PositionTransition3d> buffer;

        boolean visible;
        boolean forceInvisible; // just in case
        boolean isAvatar = false;

        public Mob(Entity entity) {
            if (entity.getId().getId() == watchedAvatar.getId().getId()) {
                this.isAvatar = true;
            }

            this.entity = entity;
            this.model = getModel(entity.getId(), true);
            model.setDynamic(true);
        }

        // public void setCenterCellWorld( Vector3f centerCellWorld ) {
        // this.centerCellWorld.set(centerCellWorld);
        // }
        public void setShape(ShapeInfo shapeInfo) {
            model.setShape(shapeInfo);
            /*
             * if( model.spatial != null ) { resetVisibility(); }
             */
        }

        public void setPosition(BodyPosition pos) {
            if (this.pos == pos) {
                return;
            }

            // BodyPosition requires special management to make
            // sure all instances of BodyPosition are sharing the same
            // thread-safe history buffer. Everywhere it's used, it should
            // be 'initialized'.
            pos.initialize(entity.getId(), 12);
            this.buffer = pos.getBuffer();

            if (this.isAvatar) {
                setAvatarBuffer(buffer);
            }
        }

        public void update(long time) {

            // Look back in the brief history that we've kept and
            // pull an interpolated value. To do this, we grab the
            // span of time that contains the time we want. PositionTransition3d
            // represents a starting and an ending pos+rot over a span of time.
            PositionTransition3d trans = buffer.getTransition(time);
            if (trans != null) {
                Vector3f pos = trans.getPosition(time, true).toVector3f();

                // Make the position relative to our "conveyor"
                pos.subtractLocal(centerCellWorld);

                model.spatial.setLocalTranslation(pos);
                model.spatial.setLocalRotation(trans.getRotation(time, true).toQuaternion());
                // log.info("Mob[" + entity.getId() + "] position:" +
                // model.spatial.getLocalTranslation());
                setVisible(trans.getVisibility(time));

                if (isAvatar) {
                    Vector3f avatarWorldPos = model.spatial.getWorldTranslation();
                    getApplication().getCamera().setLocation(avatarWorldPos.add(0, 40, 0));
                    getApplication().getCamera().lookAt(avatarWorldPos, Vector3f.UNIT_Y);

                    getState(WorldViewState.class).setViewLocation(pos.clone().addLocal(centerCellWorld));
                    gameSession.setView(new Quatd(getApplication().getCamera().getRotation()),
                            new Vec3d(getApplication().getCamera().getLocation()));
                }
            }
        }

        protected void setVisible(boolean f) {
//log.info("setVisible(" + entity.getId() + ", " + f + ")");
            if (this.visible == f) {
                return;
            }
// For now, ignore setting false
//if( !f ) return;
            this.visible = f;
            // resetVisibility();
            if (visible) {
                model.markVisible();
            } else {
                model.markInvisible();
            }
        }

        /*
         * protected void resetVisibility() { //if( model.useCount > 1 || (visible &&
         * !forceInvisible) ) { if( (visible && !forceInvisible) ) {
         * log.info("Setting visible:" + entity.getId());
         * //model.spatial.setCullHint(Spatial.CullHint.Inherit); model.markVisible(); }
         * else { log.info("Setting NOT visible:" + entity.getId());
         * //model.spatial.setCullHint(Spatial.CullHint.Always); model.markInvisible();
         * } }
         */
        public void release() {
            releaseModel(entity.getId());
            model.setDynamic(false);
            // model.release();
            /*
             * if( model != null ) { model.removeFromParent(); }
             */
        }
    }

    private class MobContainer extends EntityContainer<Mob> {

        public MobContainer(EntityData ed) {
            // Because at least in this demo, shape and model are the same thing
            super(ed, BodyPosition.class, ShapeInfo.class);
        }

        public Mob[] getArray() {
            return super.getArray();
        }

        protected Mob addObject(Entity e) {
            log.info("add mob for:" + e.getId());
            Mob object = new Mob(e);
            updateObject(object, e);
            return object;
        }

        protected void updateObject(Mob object, Entity e) {
            object.setShape(e.get(ShapeInfo.class));
            object.setPosition(e.get(BodyPosition.class));
        }

        protected void removeObject(Mob object, Entity e) {
            log.info("remove mob for:" + e.getId());
            object.release();
        }
    }

    /**
     * Keeps track of the static models in the scene.
     */
    private class ModelContainer extends EntityContainer<Model> {

        public ModelContainer(EntityData ed) {
            super(ed, SpawnPosition.class, ShapeInfo.class);
        }

        public void setFilter(ComponentFilter filter) {
            super.setFilter(filter);
        }

        public Model[] getArray() {
            return (Model[]) super.getArray();
        }

        protected Model addObject(Entity e) {
//log.info("add model for:" + e.getId() + "   at time:" + timeSource.getTime());
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
            log.info("remove model for:" + e.getId());
            releaseModel(e.getId());
        }
    }

    /**
     * Keeps track of the large static models in the scene.
     */
    private class LargeModelContainer extends EntityContainer<Model> {

        public LargeModelContainer(EntityData ed) {
            super(ed, SpawnPosition.class, ShapeInfo.class, LargeObject.class, LargeGridCell.class);
        }

        public void setFilter(ComponentFilter filter) {
            super.setFilter(filter);
        }

        public Model[] getArray() {
            return (Model[]) super.getArray();
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

    public TileType getType(EntityId eId) {
        return tileTypes.getEntity(eId).get(TileType.class);
    }

    public Spatial getModelSpatial(EntityId eId, boolean throwNotExists) {
        if (throwNotExists && !modelIndex.containsKey(eId)) {
            throw new NoSuchElementException("Entity " + eId + " does not have a spatial");
        }
        return spatialIndex.get(eId);
    }

    private void setAvatarBuffer(TransitionBuffer buffer) {
        this.avatarBuffer = buffer;
    }

    public TransitionBuffer<PositionTransition3d> getAvatarBuffer() {
        return this.avatarBuffer;
    }

    private void setAvatarWorldPosition(Vector3f pos) {
        this.avatarPos = pos;
    }

    public Vector3f getAvatarPosition() {
        return this.avatarPos;
    }
}
