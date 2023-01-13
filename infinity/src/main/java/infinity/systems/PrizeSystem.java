/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.*;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import java.util.*;

import infinity.es.*;
import infinity.server.GameServer;
import infinity.sim.CategoryFilter;
import infinity.sim.CollisionFilters;
import infinity.sim.GameEntities;
import infinity.sim.InfinityContactDispatcher;
import infinity.util.RandomSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Asser
 */
public class PrizeSystem extends AbstractGameSystem implements ContactListener {

    private final PhysicsSpace phys;
    private EntityData ed;

    private EntitySet prizeSpawners;

    private HashMap<EntityId, HashSet<EntityId>> spawnerBounties = new HashMap<>();
    private HashMap<String, Integer> prizeWeights = new HashMap<>();

    RandomSelector<String> rc;
    Random random;
    private EntitySet prizes;
    static Logger log = LoggerFactory.getLogger(PrizeSystem.class);
    private long ourTime;
    private EntitySet ships;
    private InfinityContactDispatcher dispatcher;

    public PrizeSystem(PhysicsSpace phys){
        this.phys = phys;
    }

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);

        ComponentFilter prizeSpawnerFilter = FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.Prizes);

        prizeSpawners = ed.getEntities(prizeSpawnerFilter, Spawner.class, SpawnPosition.class, SphereShape.class);

        //TODO: Read prize weights and load into random collection
        random = new Random();

        this.loadPrizeWeights();

        rc = RandomSelector.weighted(prizeWeights.keySet(), s -> prizeWeights.get(s));

        ComponentFilter shipColliderFilter = FieldFilter.create(CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS);
        ComponentFilter prizeColliderFilter = FieldFilter.create(CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS);

        ships = ed.getEntities(shipColliderFilter);
        prizes = ed.getEntities(prizeColliderFilter, PrizeType.class);

        dispatcher = getSystem(InfinityContactDispatcher.class);
        dispatcher.addListener(this);
    }

    private void loadPrizeWeights() {
        prizeWeights.put(PrizeTypes.ALLWEAPONS, 40);
        prizeWeights.put(PrizeTypes.ANTIWARP, 40);
        prizeWeights.put(PrizeTypes.BOMB, 40);
        prizeWeights.put(PrizeTypes.BOUNCINGBULLETS, 40);
        prizeWeights.put(PrizeTypes.BRICK, 40);
        prizeWeights.put(PrizeTypes.BURST, 40);
        prizeWeights.put(PrizeTypes.CLOAK, 40);
        prizeWeights.put(PrizeTypes.DECOY, 40);
        prizeWeights.put(PrizeTypes.ENERGY, 40);
        prizeWeights.put(PrizeTypes.GLUE, 40);
        prizeWeights.put(PrizeTypes.GUN, 40);
        prizeWeights.put(PrizeTypes.MULTIFIRE, 40);
        prizeWeights.put(PrizeTypes.MULTIPRIZE, 40);
        prizeWeights.put(PrizeTypes.PORTAL, 40);
        prizeWeights.put(PrizeTypes.PROXIMITY, 40);
        prizeWeights.put(PrizeTypes.QUICKCHARGE, 40);
        prizeWeights.put(PrizeTypes.RECHARGE, 40);
        prizeWeights.put(PrizeTypes.REPEL, 40);
        prizeWeights.put(PrizeTypes.ROCKET, 40);
        prizeWeights.put(PrizeTypes.ROTATION, 40);
        prizeWeights.put(PrizeTypes.SHIELDS, 40);
        prizeWeights.put(PrizeTypes.SHRAPNEL, 40);
        prizeWeights.put(PrizeTypes.STEALTH, 40);
        prizeWeights.put(PrizeTypes.THOR, 40);
        prizeWeights.put(PrizeTypes.THRUSTER, 40);
        prizeWeights.put(PrizeTypes.TOPSPEED, 40);
        prizeWeights.put(PrizeTypes.WARP, 40);
        prizeWeights.put(PrizeTypes.XRADAR, 40);
    }

    @Override
    protected void terminate() {
        prizes.release();
        prizes = null;

        //ships.release();
        //ships = null;

        prizeSpawners.release();
        prizeSpawners = null;

        dispatcher.removeListener(this);
    }

    @Override
    public void update(SimTime time) {
        this.ourTime = time.getTime();

        prizes.applyChanges();

        //Updated count if prizes are removed
        for(Entity eBountyRemoved : prizes.getRemovedEntities()){
            EntityId idBounty = eBountyRemoved.getId();
            for (Entity entitySpawner : prizeSpawners) {
                HashSet<EntityId> spawnerBountySet = spawnerBounties.get(entitySpawner.getId());
                spawnerBountySet.remove(idBounty);
                spawnerBounties.put(entitySpawner.getId(),spawnerBountySet);
                //log.info(String.valueOf(spawnerBountySet.size()));
            }
        }

        //ships.applyChanges();

        prizeSpawners.applyChanges();

        for (Entity entitySpawner : prizeSpawners) { //Spawn max one per update-call / frame
            Spawner s = entitySpawner.get(Spawner.class);
            SpawnPosition p = entitySpawner.get(SpawnPosition.class);
            SphereShape c = entitySpawner.get(SphereShape.class);
            if (spawnerBounties.containsKey(entitySpawner.getId()) && spawnerBounties.get(entitySpawner.getId()).size() < s.getMaxCount()) {
                EntityId idBounty = spawnRandomBounty(p.getLocation(), c.getRadius(), false);

                HashSet<EntityId> spawnerBountySet = spawnerBounties.get(entitySpawner.getId());
                spawnerBountySet.add(idBounty);
                spawnerBounties.put(entitySpawner.getId(),spawnerBountySet);
                //log.info(String.valueOf(spawnerBountySet.size()));
            } else if(!spawnerBounties.containsKey(entitySpawner.getId())){
                EntityId idBounty = spawnRandomBounty(p.getLocation(), c.getRadius(), false);

                HashSet<EntityId> spawnerBountySet = new HashSet<>();
                spawnerBountySet.add(idBounty);
                spawnerBounties.put(entitySpawner.getId(),spawnerBountySet);
                //log.info(String.valueOf(spawnerBountySet.size()));
            }
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    private Vec3d getRandomWeightedCircleSpawnLocation(Vec3d spawnCenter, double radius, boolean onlyOnCistringWeightsumference) {
        double angle = Math.random() * Math.PI * 2;

        double lengthFromCenter = onlyOnCistringWeightsumference ? radius : radius * Math.random();

        double x = Math.cos(angle) * lengthFromCenter + spawnCenter.x;
        double z = Math.sin(angle) * lengthFromCenter + spawnCenter.z;

        return new Vec3d(x, spawnCenter.y, z);
    }

    private EntityId spawnRandomBounty(Vec3d spawnCenter, double radius, boolean onlyOnCistringWeightsumference) {
        Vec3d location = this.getRandomWeightedCircleSpawnLocation(spawnCenter, radius, onlyOnCistringWeightsumference);

        return GameEntities.createPrize(ed, phys, ourTime, location, PrizeTypes.BRICK);
    }

    private void handlePrizeAcquisition(PrizeType pt, EntityId ship) {
        log.info("Ship " + ship + " picked up prize:" + pt.getTypeName(ed));
        switch (pt.getTypeName(ed)) {
            case PrizeTypes.ALLWEAPONS:
                break;
            case PrizeTypes.ANTIWARP:
                break;
            case PrizeTypes.BOMB:
                break;
            case PrizeTypes.BOUNCINGBULLETS:
                break;
            case PrizeTypes.BRICK:
                break;
            case PrizeTypes.BURST:
                break;
            case PrizeTypes.CLOAK:
                break;
            case PrizeTypes.DECOY:
                break;
            case PrizeTypes.ENERGY:
                break;
            case PrizeTypes.GLUE:
                break;
            case PrizeTypes.GUN:
                break;
            case PrizeTypes.MULTIFIRE:
                break;
            case PrizeTypes.MULTIPRIZE:
                break;
            case PrizeTypes.PORTAL:
                break;
            case PrizeTypes.PROXIMITY:
                break;
            case PrizeTypes.QUICKCHARGE:
                break;
            case PrizeTypes.RECHARGE:
                break;
            case PrizeTypes.REPEL:
                break;
            case PrizeTypes.ROCKET:
                break;
            case PrizeTypes.ROTATION:
                break;
            case PrizeTypes.SHIELDS:
                break;
            case PrizeTypes.SHRAPNEL:
                break;
            case PrizeTypes.STEALTH:
                break;
            case PrizeTypes.THOR:
                break;
            case PrizeTypes.THRUSTER:
                break;
            case PrizeTypes.TOPSPEED:
                break;
            case PrizeTypes.WARP:
                break;
            case PrizeTypes.XRADAR:
                break;
            default:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported by");
        }
    }

    @Override
    public void newContact(Contact contact) {
        log.info("PrizeSystem collision detected: "+contact.toString());
        /*
        RigidBody body1 = contact.getBody1();
        RigidBody body2 = contact.getBody2();

        EntityId idOne = (EntityId) body1.id;
        EntityId idTwo = (EntityId) body2.id;

        CollisionCategory filter1 = ed.getComponent(idOne, CollisionCategory.class);
        CollisionCategory filter2 = ed.getComponent(idTwo, CollisionCategory.class);

        if(filter1.getFilter().isAllowed(filter2.getFilter())){
            log.info("Filter allows the contact");
        }
        else{
            log.info("Filter DOES NOT allow the contact");
        }

        //Only interact with collision if a ship collides with a prize or vice verca
        if (prizes.containsId(idOne) && ships.containsId(idTwo)) {
            log.info("Entitysets contact resolution found it to be valid");
            PrizeType pt = prizes.getEntity(idOne).get(PrizeType.class);
            this.handlePrizeAcquisition(pt, idTwo);
            //Remove prize
            ed.removeEntity(idOne);
            //Disable contact for further resolution
            contact.disable();
        } else if (prizes.containsId(idTwo) && ships.containsId(idOne)) {
            log.info("Entitysets contact resolution found it to be valid");
            PrizeType pt = prizes.getEntity(idTwo).get(PrizeType.class);
            this.handlePrizeAcquisition(pt, idOne);
            //Remove prize
            ed.removeEntity(idTwo);
            //Disable contact for further resolution
            contact.disable();
        }
        */
    }
}
