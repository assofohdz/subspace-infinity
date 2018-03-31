/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import example.es.states.tools.RandomSelector;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.GameConstants;
import example.es.Position;
import example.es.ShipType;
import example.es.Spawner;
import example.es.SphereShape;
import example.es.ship.weapons.Thor;
import example.es.subspace.PrizeType;
import example.es.subspace.PrizeTypes;
import example.sim.CoreGameEntities;
import example.sim.SimplePhysics;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Asser
 */
public class PrizeState extends AbstractGameSystem implements CollisionListener {

    private EntityData ed;

    private Map<EntityId, Integer> prizeCount = new HashMap<EntityId, Integer>();
    private EntitySet prizeSpawners;

    private HashMap<String, Integer> prizeWeights = new HashMap<>();

    RandomSelector<String> rc;
    Random random;
    private EntitySet prizes;
    private EntitySet ships;
    static Logger log = LoggerFactory.getLogger(PrizeState.class);
    private SimplePhysics simplePhysics;

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);

        ComponentFilter prizeSpawnerFilter = FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.Prizes);

        prizeSpawners = ed.getEntities(prizeSpawnerFilter, Spawner.class, Position.class, SphereShape.class);

        //TODO: Read prize weights and load into random collection
        random = new Random();

        this.loadPrizeWeights();

        rc = RandomSelector.weighted(prizeWeights.keySet(), s -> prizeWeights.get(s));

        ships = ed.getEntities(ShipType.class);
        prizes = ed.getEntities(PrizeType.class);

        this.simplePhysics = getSystem(SimplePhysics.class);
        this.simplePhysics.addCollisionListener(this);
    }

    private void loadPrizeWeights() {
        prizeWeights.put(PrizeTypes.ALLWEAPONS, 0);
        prizeWeights.put(PrizeTypes.ANTIWARP, 0);
        prizeWeights.put(PrizeTypes.BOMB, 0);
        prizeWeights.put(PrizeTypes.BOUNCINGBULLETS, 0);
        prizeWeights.put(PrizeTypes.BRICK, 0);
        prizeWeights.put(PrizeTypes.BURST, 0);
        prizeWeights.put(PrizeTypes.CLOAK, 0);
        prizeWeights.put(PrizeTypes.DECOY, 0);
        prizeWeights.put(PrizeTypes.ENERGY, 0);
        prizeWeights.put(PrizeTypes.ENERGY, 0);
        prizeWeights.put(PrizeTypes.GLUE, 0);
        prizeWeights.put(PrizeTypes.GUN, 0);
        prizeWeights.put(PrizeTypes.MULTIFIRE, 0);
        prizeWeights.put(PrizeTypes.MULTIPRIZE, 0);
        prizeWeights.put(PrizeTypes.PORTAL, 0);
        prizeWeights.put(PrizeTypes.PROXIMITY, 0);
        prizeWeights.put(PrizeTypes.QUICKCHARGE, 0);
        prizeWeights.put(PrizeTypes.RECHARGE, 0);
        prizeWeights.put(PrizeTypes.REPEL, 0);
        prizeWeights.put(PrizeTypes.ROCKET, 0);
        prizeWeights.put(PrizeTypes.ROTATION, 0);
        prizeWeights.put(PrizeTypes.SHIELDS, 0);
        prizeWeights.put(PrizeTypes.SHRAPNEL, 0);
        prizeWeights.put(PrizeTypes.STEALTH, 0);
        prizeWeights.put(PrizeTypes.THOR, 100);
        prizeWeights.put(PrizeTypes.THRUSTER, 0);
        prizeWeights.put(PrizeTypes.TOPSPEED, 0);
        prizeWeights.put(PrizeTypes.WARP, 0);
        prizeWeights.put(PrizeTypes.XRADAR, 0);
    }

    @Override
    protected void terminate() {
        prizes.release();
        prizes = null;

        ships.release();
        ships = null;

        prizeSpawners.release();
        prizeSpawners = null;
    }

    @Override
    public void update(SimTime time) {
        prizes.applyChanges();
        ships.applyChanges();

        prizeSpawners.applyChanges();

        for (Entity e : prizeSpawners) { //Spawn max one per update-call / frame
            Spawner s = e.get(Spawner.class);
            Position p = e.get(Position.class);
            SphereShape c = e.get(SphereShape.class);
            if (prizeCount.containsKey(e.getId()) && prizeCount.get(e.getId()) < s.getMaxCount()) {
                spawnRandomWeightedPrize(p.getLocation(), c.getRadius(), false);
                prizeCount.put(e.getId(), prizeCount.get(e.getId()) + 1);
            } else {
                spawnRandomWeightedPrize(p.getLocation(), c.getRadius(), false);
                prizeCount.put(e.getId(), 1);
            }
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    private Vec3d getRandomSpawnLocation(Vec3d spawnCenter, double radius, boolean onlyOnCistringWeightsumference) {
        double angle = Math.random() * Math.PI * 2;

        double lengthFromCenter = onlyOnCistringWeightsumference ? radius : radius * Math.random();

        double x = Math.cos(angle) * lengthFromCenter + spawnCenter.x;
        double y = Math.sin(angle) * lengthFromCenter + spawnCenter.y;

        return new Vec3d(x, y, 0);
    }

    private EntityId spawnRandomWeightedPrize(Vec3d spawnCenter, double radius, boolean onlyCircumference) {
        Vec3d location = this.getRandomSpawnLocation(spawnCenter, radius, onlyCircumference);

        return CoreGameEntities.createPrize(location, rc.next(random), ed);
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2) {
        return true;
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2, Penetration penetration) {
        return true;
    }

    //Contact manifold created by the manifold solver
    @Override
    public boolean collision(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2, Manifold manifold) {
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        //Only interact with collision if a ship collides with a prize or vice verca
        if (prizes.containsId(one) && ships.containsId(two)) {
            PrizeType pt = prizes.getEntity(one).get(PrizeType.class);
            this.handlePrizeAcquisition(pt, two);
            //Remove prize
            ed.removeEntity(one);
            return false;
        } else if (prizes.containsId(two) && ships.containsId(one)) {
            PrizeType pt = prizes.getEntity(two).get(PrizeType.class);
            this.handlePrizeAcquisition(pt, one);
            //Remove prize
            ed.removeEntity(two);
            return false;
        }

        return true;
    }

    //Contact constraint created
    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true;
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
                Thor t = ed.getComponent(ship, Thor.class);
                if (t == null) {
                    t = new Thor(GameConstants.THORCOOLDOWN, 1);
                } else {
                    int count = t.getCount();
                    t = new Thor(GameConstants.THORCOOLDOWN, count + 1);
                }
                ed.setComponent(ship, t);

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
}
