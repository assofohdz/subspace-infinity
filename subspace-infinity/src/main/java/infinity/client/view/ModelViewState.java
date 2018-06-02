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

import infinity.client.view.SISpatialFactory;
import infinity.api.es.Position;
import infinity.api.es.BodyPosition;
import infinity.api.es.TileType;
import infinity.api.es.SphereShape;
import infinity.api.es.ViewTypes;
import infinity.api.es.ViewType;
import java.util.*;

import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.*;
import com.jme3.material.Material;
import com.jme3.scene.*;
import com.jme3.scene.debug.Arrow;
import com.jme3.scene.shape.*;
import com.jme3.texture.Texture;

import com.simsilica.lemur.*;

import com.simsilica.es.*;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.es.filter.OrFilter;
import com.simsilica.ethereal.LocalZoneIndex;
import com.simsilica.ethereal.zone.ZoneKey;
import com.simsilica.mathd.Vec3d;

import com.simsilica.mathd.trans.PositionTransition;
import com.simsilica.mathd.trans.TransitionBuffer;

import infinity.ConnectionState;
import infinity.ServerGameConstants;
import infinity.GameSessionState;
import infinity.Main;
import infinity.TimeState;
import infinity.client.MapStateClient;

/**
 * Displays the models for the various physics objects.
 *
 * @author Paul Speed
 */
public class ModelViewState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(ModelViewState.class);

    private EntityData ed;
    private TimeState timeState;

    private Node modelRoot;

    private Map<EntityId, Spatial> modelIndex = new HashMap<>();

    private MobContainer mobs;
    private ModelContainer models;
    private SISpatialFactory factory;
    private Spatial playerSpatial;
    private EntityId localPlayerEntityId;
    private EntitySet tileTypes;
    private Spatial arenaSpatial;

    private long oldPlayerCellId;

    private LocalZoneIndex zones;

    private ModelViewState mvs;
    private Vec3d oldPlayerPosition;
    private boolean playerCellIdInitialized = false;
    
    private List<ZoneKey> inZones = new ArrayList<>();

    public ModelViewState(SISpatialFactory siSpatialFactory) {
        this.factory = siSpatialFactory;
    }

    public Spatial getModel(EntityId id) {
        return modelIndex.get(id);
    }

    public ModelContainer getModelContainer() {
        return models;
    }

    public MobContainer getMobContainer() {
        return mobs;
    }

    @Override
    protected void initialize(Application app) {
        factory.setState(this);
        modelRoot = new Node();

        zones = new LocalZoneIndex(ServerGameConstants.ZONE_GRID, ServerGameConstants.ZONE_RADIUS);

        // Retrieve the time source from the network connection
        // The time source will give us a time in recent history that we should be
        // viewing.  This currently defaults to -100 ms but could vary (someday) depending
        // on network connectivity.
        // For more information on this interpolation approach, see the Valve networking
        // articles at:
        // https://developer.valvesoftware.com/wiki/Source_Multiplayer_Networking
        // https://developer.valvesoftware.com/wiki/Latency_Compensating_Methods_in_Client/Server_In-game_Protocol_Design_and_Optimization
        //this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();
        // 
        // We now grab time from the TimeState which wraps the TimeSource to give
        // consistent timings over the whole frame
        this.timeState = getState(TimeState.class);

        this.ed = getState(ConnectionState.class).getEntityData();

        this.tileTypes = ed.getEntities(TileType.class);
    }

    @Override
    protected void cleanup(Application app) {
        tileTypes.release();
        tileTypes = null;
    }

    @Override
    protected void onEnable() {

        mobs = new MobContainer(ed);
        models = new ModelContainer(ed);
        mobs.start();
        models.start();

        ((Main) getApplication()).getRootNode().attachChild(modelRoot);
    }

    @Override
    protected void onDisable() {
        modelRoot.removeFromParent();

        models.stop();
        mobs.stop();
        mobs = null;
        models = null;
    }

    @Override
    public void update(float tpf) {

        if (playerSpatial != null) {
            Vec3d newPlayerPosition = new Vec3d(playerSpatial.getWorldTranslation());
            if (newPlayerPosition != oldPlayerPosition) {
                ZoneKey newKey = ServerGameConstants.ZONE_GRID.worldToKey(newPlayerPosition);
                long newPlayerCellId = newKey.toLongId();
                if (newPlayerCellId != oldPlayerCellId || !playerCellIdInitialized) {

                    List<ZoneKey> entered = new ArrayList<>();
                    List<ZoneKey> exited = new ArrayList<>();
                    zones.setCenter(newKey, entered, exited);

                    inZones.addAll(entered);
                    inZones.removeAll(exited);
                    
                    OrFilter zoneFilter = generateZoneFilter(inZones);

                    models.updateFilter(zoneFilter);

                    oldPlayerCellId = newPlayerCellId;

                    playerCellIdInitialized = true;
                    
                }
                oldPlayerPosition = newPlayerPosition;
            }
        }

        tileTypes.applyChanges();

        //tileImages.update();
        // Grab a consistent time for this frame
        long time = timeState.getTime();

        // Update all of the models
        models.update();
        mobs.update();
        for (Mob mob : mobs.getArray()) {
            mob.updateSpatial(time);
        }

        if (tileTypes.hasChanges()) {
            //Tile index has updated
            for (Entity e : tileTypes.getChangedEntities()) {
                //There's a timing issue I can't figure out yet
                if (modelIndex.containsKey(e.getId())) {
                    TileType tt = e.get(TileType.class);

                    factory.updateWangBlobTile(getModelSpatial(e.getId(), true), tt);
                }
            }
        }
        //log.info("worldToZone("+playerSpatial.getWorldTranslation()+") ="+GameConstants.ZONE_GRID.worldToZone(new Vec3d(playerSpatial.getWorldTranslation())));
    }

    protected Spatial createShip(Entity entity) {
        //Spatial information:
        Spatial ship = factory.createModel(entity);

        //Node information:
        Node result = new Node("ship:" + entity.getId());
        result.setUserData("entityId", entity.getId().getId());
        result.attachChild(ship);
        //result.setUserData(LayerComparator.LAYER, 0);

        //attachCoordinateAxes(result); //To debug
        return result;
    }

    protected Spatial createBase(Entity entity) {
        //Node information:
        Node result = new Node("base:" + entity.getId());
        result.setUserData("baseId", entity.getId().getId());

        //Spatial information:
        Spatial base = factory.createModel(entity);
        result.attachChild(base);

        //attachCoordinateAxes(result);
        return result;
    }

    protected Spatial createTower(Entity entity) {
        //Node information:
        Node result = new Node("tower:" + entity.getId());
        result.setUserData("towerId", entity.getId().getId());

        //Spatial information:
        Spatial tower = factory.createModel(entity);
        result.attachChild(tower);

        //attachCoordinateAxes(result);
        return result;
    }

    protected Spatial createMob(Entity entity) {
        //Node information:
        Node result = new Node("mob:" + entity.getId());
        result.setUserData("mobId", entity.getId().getId());

        //Spatial information:
        Spatial mob = factory.createModel(entity);
        result.attachChild(mob);

        //attachCoordinateAxes(result);
        return result;
    }

    protected Spatial createGravSphere(Entity entity) {

        SphereShape shape = ed.getComponent(entity.getId(), SphereShape.class);
        float radius = shape == null ? 1 : (float) shape.getRadius();

        GuiGlobals globals = GuiGlobals.getInstance();
        Sphere sphere = new Sphere(40, 40, radius);
        sphere.setTextureMode(Sphere.TextureMode.Projected);
        sphere.scaleTextureCoordinates(new Vector2f(60, 40));
        Geometry geom = new Geometry("test", sphere);
        Texture texture = globals.loadTexture("Textures/gravsphere.png", true, true);
        //Material mat = globals.createMaterial(texture, false).getMaterial();
        Material mat = new Material(getApplication().getAssetManager(), "MatDefs/FogUnshaded.j3md");
        mat.setTexture("ColorMap", texture);
        mat.setColor("FogColor", new ColorRGBA(0, 0, 0.1f, 1));
        mat.setFloat("FogDepth", 256);
        geom.setMaterial(mat);

        geom.setLocalTranslation(16, 16, 16);
        geom.rotate(-FastMath.HALF_PI, 0, 0);

        return geom;
    }

    protected Spatial createBomb(Entity entity) {
        //Node information:
        Node result = new Node("bomb:" + entity.getId());
        result.setUserData("bombId", entity.getId().getId());

        //Spatial information:
        Spatial bomb = factory.createModel(entity);
        result.attachChild(bomb);

        //attachCoordinateAxes(result);
        return result;
    }

    protected Spatial createThor(Entity entity) {
        //Node information:
        Node result = new Node("thor:" + entity.getId());
        result.setUserData("thorId", entity.getId().getId());

        //Spatial information:
        Spatial bomb = factory.createModel(entity);
        result.attachChild(bomb);

        //attachCoordinateAxes(result);
        return result;
    }

    protected Spatial createBullet(Entity entity) {
        //Node information:
        Node result = new Node("bullet:" + entity.getId());
        result.setUserData("bulletId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial bullet = factory.createModel(entity);
        result.attachChild(bullet);

        return result;
    }

    protected Spatial createExplosion2(Entity entity) {
        //Node information:
        Node result = new Node("explosion2:" + entity.getId());
        result.setUserData("explosion2Id", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial bullet = factory.createModel(entity);
        result.attachChild(bullet);

        return result;
    }

    protected Spatial createWormhole(Entity entity) {
        //Node information:
        Node result = new Node("wormhole:" + entity.getId());
        result.setUserData("wormholeId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial gravityWell = factory.createModel(entity);
        result.attachChild(gravityWell);

        return result;
    }

    protected Spatial createBounty(Entity entity) {
        //Node information:
        Node result = new Node("bounty:" + entity.getId());
        result.setUserData("bountyId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial bounty = factory.createModel(entity);
        result.attachChild(bounty);

        //attachCoordinateAxes(result);
        return result;
    }

    protected Spatial createModel(Entity entity) {
        // Check to see if one already exists
        Spatial result = modelIndex.get(entity.getId());
        if (result != null) {
            return result;
        }

        // Else figure out what type to create... 
        ViewType type = entity.get(ViewType.class);
        String typeName = type.getTypeName(ed);
        switch (typeName) {
            case ViewTypes.FLAG_OURS:
            case ViewTypes.FLAG_THEIRS:
                result = createFlag(entity);
                break;
            case ViewTypes.GRAV_SPHERE:
                result = createGravSphere(entity);
                break;
            case ViewTypes.PRIZE:
                result = createBounty(entity);
                break;
            case ViewTypes.ARENA:
                result = createArena(entity);
                break;
            case ViewTypes.MAPTILE:
                result = createMapTile(entity);
                break;
            case ViewTypes.EXPLOSION2:
                result = createExplosion2(entity);
                break;
            case ViewTypes.WORMHOLE:
                result = createWormhole(entity);
                break;
            case ViewTypes.OVER5:
                result = createOver5(entity);
                break;
            case ViewTypes.OVER1:
                result = createOver1(entity);
                break;
            case ViewTypes.OVER2:
                result = createOver2(entity);
                break;
            case ViewTypes.TOWER:
                result = createTower(entity);
                break;
            case ViewTypes.MOB:
                result = createMob(entity);
                break;
            case ViewTypes.BASE:
                result = createBase(entity);
                break;
            //SHIPS
            case ViewTypes.SHIP_SHARK:
            case ViewTypes.SHIP_WARBIRD:
            case ViewTypes.SHIP_JAVELIN:
            case ViewTypes.SHIP_SPIDER:
            case ViewTypes.SHIP_LEVI:
            case ViewTypes.SHIP_LANCASTER:
            case ViewTypes.SHIP_WEASEL:
            case ViewTypes.SHIP_TERRIER:
                result = createShip(entity);
                break;
            //WEAPONS
            case ViewTypes.BULLETL1:
            case ViewTypes.BULLETL2:
            case ViewTypes.BULLETL3:
            case ViewTypes.BULLETL4:
                result = createBullet(entity);
                break;
            case ViewTypes.BOMBL1:
            case ViewTypes.BOMBL2:
            case ViewTypes.BOMBL3:
            case ViewTypes.BOMBL4:
                result = createBomb(entity);
                break;
            case ViewTypes.THOR:
                result = createThor(entity);
                break;
            case ViewTypes.BURST:
                result = createBurst(entity);
            //ACTIONS
            case ViewTypes.WARP:
                result = createWarp(entity);
                break;
            case ViewTypes.REPEL:
                result = createRepel(entity);
                break;
            default:
                throw new RuntimeException("Unknown spatial type:" + typeName);
        }

        // Add it to the index
        modelIndex.put(entity.getId(), result);
        modelRoot.attachChild(result);

        return result;
    }

    protected void updateModel(Spatial spatial, Entity entity, boolean updatePosition) {
        if (updatePosition) {
            Position pos = entity.get(Position.class);

            // I like to move it... move it...
            spatial.setLocalTranslation(pos.getLocation().toVector3f());
            spatial.setLocalRotation(pos.getFacing().toQuaternion());

            //log.info("Position ("+spatial.getName()+"): "+spatial.getLocalTranslation()+", "+pos.getFacing());
        }
    }

    protected void removeModel(Spatial spatial, Entity entity) {
        modelIndex.remove(entity.getId());
        spatial.removeFromParent();
    }

    private Spatial createArena(Entity entity) {
        //Node information:
        Node result = new Node("arena:" + entity.getId());
        result.setUserData("arenaId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial arena = factory.createModel(entity);

        this.arenaSpatial = arena;

        result.attachChild(arena);

        getState(MapStateClient.class).addArenaMouseListeners(arena);

        //attachCoordinateAxes(result);
        return result;
    }

    private Spatial createMapTile(Entity entity) {
        //Node information:
        Node result = new Node("maptile:" + entity.getId());
        result.setUserData("mapTileId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial arena = factory.createModel(entity);

        //TODO: Maybe we could use a listener on every map tile?
        //CursorEventControl.addListenersToSpatial(arena, getState(MapEditorState.class));
        result.attachChild(arena);

        //attachCoordinateAxes(result);
        return result;
    }

    protected Spatial createOver5(Entity entity) {
        //Node information:
        Node result = new Node("over5:" + entity.getId());
        result.setUserData("over5Id", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial gravityWell = factory.createModel(entity);
        result.attachChild(gravityWell);

        return result;
    }

    private Spatial createOver2(Entity entity) {
        //Node information:
        Node result = new Node("over2:" + entity.getId());
        result.setUserData("over2Id", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial gravityWell = factory.createModel(entity);
        result.attachChild(gravityWell);

        return result;
    }

    private Spatial createOver1(Entity entity) {
        //Node information:
        Node result = new Node("over1:" + entity.getId());
        result.setUserData("over1Id", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial gravityWell = factory.createModel(entity);
        result.attachChild(gravityWell);

        return result;
    }

    private Spatial createWarp(Entity entity) {
        //Node information:
        Node result = new Node("warp:" + entity.getId());
        result.setUserData("warpId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial gravityWell = factory.createModel(entity);
        result.attachChild(gravityWell);

        return result;
    }

    private Spatial createRepel(Entity entity) {
        //Node information:
        Node result = new Node("repel:" + entity.getId());
        result.setUserData("repelId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial gravityWell = factory.createModel(entity);
        result.attachChild(gravityWell);

        return result;
    }

    private Spatial createFlag(Entity entity) {
        //Node information:
        Node result = new Node("flag:" + entity.getId());
        result.setUserData("flagId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial flag = factory.createModel(entity);
        result.attachChild(flag);

        return result;
    }

    private Spatial createBurst(Entity entity) {
        //Node information:
        Node result = new Node("flag:" + entity.getId());
        result.setUserData("flagId", entity.getId().getId());
        //result.setUserData(LayerComparator.LAYER, 1);

        //Spatial information:
        Spatial flag = factory.createModel(entity);
        result.attachChild(flag);

        return result;
    }

    public class Mob {

        Entity entity;
        Spatial spatial;
        boolean visible;
        boolean localPlayerShip;

        TransitionBuffer<PositionTransition> buffer;

        public Mob(Entity entity) {
            this.entity = entity;

            this.spatial = createModel(entity); //createShip(entity);
            //modelRoot.attachChild(spatial);

            BodyPosition bodyPos = entity.get(BodyPosition.class);
            // BodyPosition requires special management to make
            // sure all instances of BodyPosition are sharing the same
            // thread-safe history buffer.  Everywhere it's used, it should
            // be 'initialized'.            
            bodyPos.initialize(entity.getId(), 12);
            buffer = bodyPos.getBuffer();

            // If this is the player's ship then we don't want the model
            // shown else it looks bad.  A) it's ugly.  B) the model will
            // always lag the player's turning.
            if (entity.getId().getId() == getState(GameSessionState.class).getShipId().getId()) {

                this.localPlayerShip = true;
                localPlayerEntityId = entity.getId();

                //To let the camerastate known which spatial is the player
                playerSpatial = spatial;
            }

            // Starts invisible until we know otherwise           
            resetVisibility();
        }

        public void updateSpatial(long time) {

            // Look back in the brief history that we've kept and
            // pull an interpolated value.  To do this, we grab the
            // span of time that contains the time we want.  PositionTransition
            // represents a starting and an ending pos+rot over a span of time.
            PositionTransition trans = buffer.getTransition(time);
            if (trans != null) {
                spatial.setLocalTranslation(trans.getPosition(time, true));
                spatial.setLocalRotation(trans.getRotation(time, true));
                setVisible(trans.getVisibility(time));
                //log.info(spatial.getName() + ": "+trans.toString());
            }
        }

        public Spatial getSpatial() {
            return this.spatial;
        }

        protected void updateComponents() {
            updateModel(spatial, entity, false);

            if (this.localPlayerShip) {
                ViewType type = entity.get(ViewType.class);
                int i = 0;
                if (null != type.getTypeName(ed)) {
                    switch (type.getTypeName(ed)) {
                        case ViewTypes.SHIP_WARBIRD:
                            i = 31;
                            break;
                        case ViewTypes.SHIP_JAVELIN:
                            i = 27;
                            break;
                        case ViewTypes.SHIP_SPIDER:
                            i = 23;
                            break;
                        case ViewTypes.SHIP_LEVI:
                            i = 19;
                            break;
                        case ViewTypes.SHIP_TERRIER:
                            i = 15;
                            break;
                        case ViewTypes.SHIP_WEASEL:
                            i = 11;
                            break;
                        case ViewTypes.SHIP_LANCASTER:
                            i = 7;
                            break;
                        case ViewTypes.SHIP_SHARK:
                            i = 3;
                            break;
                        default:
                            break;
                    }
                }

                factory.setShipMaterialVariables(playerSpatial, i);
                /*
                Spatial tempSpatial = this.spatial;

                this.spatial = createShip(entity);
                getState(CameraState.class).setPlayerShip(spatial);

                // Add it to the index
                modelIndex.put(entity.getId(), spatial);
                modelRoot.attachChild(spatial);

                tempSpatial.removeFromParent();
                 */
            }
        }

        protected void setVisible(boolean f) {
            if (this.visible == f) {
                return;
            }
            this.visible = f;
            resetVisibility();
        }

        public boolean isVisible() {
            return this.visible;
        }

        protected void resetVisibility() {

            if (visible) {
                spatial.setCullHint(Spatial.CullHint.Inherit);
            } else {
                spatial.setCullHint(Spatial.CullHint.Always);
            }

        }

        public void dispose() {
            if (models.getObject(entity.getId()) == null) {
                removeModel(spatial, entity);
            }
        }
    }

    public class MobContainer extends EntityContainer<Mob> {

        public MobContainer(EntityData ed) {
            super(ed, ViewType.class, BodyPosition.class);
        }

        @Override
        public Mob[] getArray() {
            return super.getArray();
        }

        @Override
        protected Mob addObject(Entity e) {
            //System.out.println("MobContainer.addObject(" + e + ")");
            return new Mob(e);
        }

        @Override
        protected void updateObject(Mob object, Entity e) {
            object.updateComponents();
        }

        @Override
        protected void removeObject(Mob object, Entity e) {
            object.dispose();
        }
    }

    /**
     * Contains the static objects... care needs to be taken that if an object
     * exists in both the MobContainer and this one that the MobContainer takes
     * precedence.
     */
    public class ModelContainer extends EntityContainer<Spatial> {

        public ModelContainer(EntityData ed) {
            super(ed, Position.class, ViewType.class);
        }

        @Override
        public Spatial[] getArray() {
            return super.getArray();
        }

        @Override
        protected Spatial addObject(Entity e) {
            //System.out.println("ModelContainer.addObject(" + e + ")");
            Spatial result = createModel(e);
            updateObject(result, e);
            return result;
        }

        @Override
        protected void updateObject(Spatial object, Entity e) {
            //log.info("Updated model on entity: " + e.toString());
            updateModel(object, e, true);
        }

        @Override
        protected void removeObject(Spatial object, Entity e) {
            if (mobs.getObject(e.getId()) == null) {
                removeModel(object, e);
            }
        }

        private void updateFilter(OrFilter zoneFilter) {
            this.setFilter(zoneFilter);
        }

    }

    public EntityId getPlayerEntityId() {
        return localPlayerEntityId;
    }

    public Spatial getPlayerSpatial() {
        return playerSpatial;
    }

    public Spatial getModelSpatial(EntityId eId, boolean throwNotExists) {
        if (throwNotExists && !modelIndex.containsKey(eId)) {
            throw new NoSuchElementException("Entity " + eId + " does not have a spatial");
        }
        return modelIndex.get(eId);
    }

    public Node getModelNode(EntityId eId) {
        if (models.getObject(eId) instanceof Node) {
            return (Node) models.getObject(eId);
        }
        return null;
    }

    private void putShape(Node n, Mesh shape, ColorRGBA color) {
        Geometry g = new Geometry("coordinate axis", shape);
        Material mat = new Material(this.getApplication().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setWireframe(true);
        mat.setColor("Color", color);
        g.setMaterial(mat);
        n.attachChild(g);
    }

    private void attachCoordinateAxes(Node n) {
        Arrow arrow = new Arrow(Vector3f.UNIT_X);
        arrow.setLineWidth(4); // make arrow thicker
        putShape(n, arrow, ColorRGBA.Red);

        arrow = new Arrow(Vector3f.UNIT_Y);
        arrow.setLineWidth(4); // make arrow thicker
        putShape(n, arrow, ColorRGBA.Green);

        arrow = new Arrow(Vector3f.UNIT_Z);
        arrow.setLineWidth(4); // make arrow thicker
        putShape(n, arrow, ColorRGBA.Blue);
    }

    /**
     * Updates the flag model to show if local player frequency owns it
     *
     * @param flagEntity the flag entity
     * @param teamFlag boolean indicating if this client owns it
     */
    public void updateFlagModel(Entity flagEntity, boolean teamFlag) {
        Spatial tempSpatial = modelIndex.get(flagEntity.getId());
        if (tempSpatial != null) {
            factory.setFlagMaterialVariables(tempSpatial, teamFlag ? 0 : 1);
        }
    }

    public SISpatialFactory getFactory() {
        return factory;
    }

    public TileType getType(EntityId eId) {
        return tileTypes.getEntity(eId).get(TileType.class);
    }

    public Spatial getArenaSpatial() {
        return this.arenaSpatial;
    }

    private OrFilter generateZoneFilter(List<ZoneKey> entered) {
        List<ComponentFilter> filters = new ArrayList<>();

        for (ZoneKey key : entered) {
            long cellId = key.toLongId();
            filters.add(new FieldFilter(Position.class, "cellId", cellId));

            log.info(key.toString() + "=" + cellId);
        }

        OrFilter newZoneFilter = new com.simsilica.es.filter.OrFilter(Position.class, filters.toArray(new ComponentFilter[filters.size()]));

        return newZoneFilter;
    }
}
