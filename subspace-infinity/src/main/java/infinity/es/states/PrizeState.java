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
package infinity.es.states;

import infinity.es.states.tools.RandomSelector;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.CoreGameConstants;
import infinity.api.es.AudioTypes;
import infinity.api.es.Position;
import infinity.api.es.ShipType;
import infinity.api.es.Spawner;
import infinity.api.es.SphereShape;
import infinity.api.es.ship.weapons.Bursts;
import infinity.api.es.ship.weapons.Thor;
import infinity.api.es.subspace.PrizeType;
import infinity.api.es.subspace.PrizeTypes;
import infinity.sim.CoreGameEntities;
import infinity.sim.SimplePhysics;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.geometry.Vector2;
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

    /**
     * Loads the prize weights. Is used when randomly selecting the prizes
     */
    private void loadPrizeWeights() {
        prizeWeights.put(PrizeTypes.ALLWEAPONS, 0);
        prizeWeights.put(PrizeTypes.ANTIWARP, 0);
        prizeWeights.put(PrizeTypes.BOMB, 0);
        prizeWeights.put(PrizeTypes.BOUNCINGBULLETS, 0);
        prizeWeights.put(PrizeTypes.BRICK, 0);
        prizeWeights.put(PrizeTypes.BURST, 100);
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
        prizeWeights.put(PrizeTypes.THOR, 0);
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
                spawnRandomWeightedPrize(p.getLocation(), c.getRadius(), true);
                prizeCount.put(e.getId(), prizeCount.get(e.getId()) + 1);
            } else {
                spawnRandomWeightedPrize(p.getLocation(), c.getRadius(), true);
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

    /**
     * Spawns a randomly selected prize
     *
     * @param spawnCenter the center of the spawn location
     * @param radius the radius of the spawn location
     * @param onlyCircumference true if only spawn on the circumference of the
     * circle
     * @return the entity id of the prize that was spawned
     */
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
            Vector2 loc = body1.getWorldCenter();
            this.handlePrizeAcquisition(pt, two);
            //Remove prize
            ed.removeEntity(one);
            //Play audio
            //Play audio
            CoreGameEntities.createSound(two, new Vec3d(loc.x, loc.y, 0), AudioTypes.PICKUP_PRIZE, ed);
            return false;
        } else if (prizes.containsId(two) && ships.containsId(one)) {
            PrizeType pt = prizes.getEntity(two).get(PrizeType.class);
            Vector2 loc = body2.getWorldCenter();
            this.handlePrizeAcquisition(pt, one);
            //Remove prize
            ed.removeEntity(two);
            //Play audio
            CoreGameEntities.createSound(one, new Vec3d(loc.x, loc.y, 0), AudioTypes.PICKUP_PRIZE, ed);
            return false;
        }

        return true;
    }

    //Contact constraint created
    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true;
    }

    /**
     * Handles a ship picking up a prize. When picking up a prize, the powerup
     * is granted to the ship.
     *
     * @param pt the prize that was picked up
     * @param ship the ship that picked up the prize
     */
    private void handlePrizeAcquisition(PrizeType pt, EntityId ship) {
        log.info("Ship " + ship + " picked up prize:" + pt.getTypeName(ed));
        switch (pt.getTypeName(ed)) {
            case PrizeTypes.ALLWEAPONS:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.ANTIWARP:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.BOMB:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.BOUNCINGBULLETS:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.BRICK:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.BURST:
                Bursts burst = ed.getComponent(ship, Bursts.class);
                if (burst == null) {
                    burst = new Bursts(CoreGameConstants.BURSTCOOLDOWN, 1);
                } else {
                    int count = burst.getCount();
                    burst = new Bursts(CoreGameConstants.BURSTCOOLDOWN, count + 1);
                }
                ed.setComponent(ship, burst);
                break;
            case PrizeTypes.CLOAK:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.DECOY:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.ENERGY:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.GLUE:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.GUN:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.MULTIFIRE:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.MULTIPRIZE:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.PORTAL:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.PROXIMITY:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.QUICKCHARGE:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.RECHARGE:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.REPEL:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.ROCKET:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.ROTATION:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.SHIELDS:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.SHRAPNEL:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.STEALTH:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.THOR:
                Thor t = ed.getComponent(ship, Thor.class);
                if (t == null) {
                    t = new Thor(CoreGameConstants.THORCOOLDOWN, 1);
                } else {
                    int count = t.getCount();
                    t = new Thor(CoreGameConstants.THORCOOLDOWN, count + 1);
                }
                ed.setComponent(ship, t);

                break;
            case PrizeTypes.THRUSTER:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.TOPSPEED:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.WARP:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.XRADAR:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            default:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
        }
    }
}
