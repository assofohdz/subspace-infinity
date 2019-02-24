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
import infinity.api.es.AudioTypes;
import infinity.api.es.Position;
import infinity.api.es.ship.ShipType;
import infinity.api.es.Spawner;
import infinity.api.es.SphereShape;
import infinity.api.es.ship.toggles.Antiwarp;
import infinity.api.es.ship.actions.Burst;
import infinity.api.es.ship.actions.Thor;
import infinity.api.es.subspace.ArenaId;
import infinity.api.es.subspace.PrizeType;
import infinity.api.es.subspace.PrizeTypes;
import infinity.api.sim.ModuleGameEntities;
import infinity.settings.SettingListener;
import infinity.sim.SimplePhysics;
import java.util.ArrayList;
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
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Asser
 */
public class PrizeState extends AbstractGameSystem implements CollisionListener, SettingListener {

    private static final String PRIZEWEIGHTSECTION = "PrizeWeight";
    private static final String PRIZERULESECITON = "Prizes";

    private static final String PRIZE_MULTIPRIZECOUNT = "MultiPrizeCount";
    private static final String PRIZE_PRIZEFACTOR = "PrizeFactor";
    private static final String PRIZE_PRIZEDELAY = "PrizeDelay";
    private static final String PRIZE_PRIZEHIDECOUNT = "PrizeHideCount";
    private static final String PRIZE_MINIMUMVIRTUAL = "MinimumVirtual";
    private static final String PRIZE_UPGRADEVIRTUAL = "UpgradeVirtual";
    private static final String PRIZE_PRIZEMAXEXIST = "PrizeMaxExist";
    private static final String PRIZE_PRIZEMINEXIST = "PrizeMinExist";
    private static final String PRIZE_PRIZENEGATIVEFACTOR = "PrizeNegativeFactor";
    private static final String PRIZE_DEATHPRIZETIME = "DeathPrizeTime";
    private static final String PRIZE_ENGINESHUTDOWNTIME = "EngineShutdownTime";
    private static final String PRIZE_TAKEPRIZERELIABLE = "TakePrizeReliable";
    private static final String PRIZE_S2CTAKEPRIZERELIABLE = "S2CTakePrizeReliable";

    /*
    Prize:MultiPrizeCount:::Number of random 'Greens' given with a 'MultiPrize'
Prize:PrizeFactor:::Number of prizes hidden is based on number of players in game.  This number adjusts the formula, higher numbers mean more prizes. (*Note: 10000 is max, 10 greens per person)
Prize:PrizeDelay:::How often prizes are regenerated (in hundredths of a second)
Prize:PrizeHideCount:::Number of prizes that are regenerated every PrizeDelay.
Prize:MinimumVirtual:::Distance from center of arena that prizes/flags/soccer-balls will generate
Prize:UpgradeVirtual:::Amount of additional distance added to MinimumVirtual for each player that is in the game.
Prize:PrizeMaxExist:::Maximum amount of time that a hidden prize will remain on screen. (actual time is random)
Prize:PrizeMinExist:::Minimum amount of time that a hidden prize will remain on screen. (actual time is random)
Prize:PrizeNegativeFactor:::Odds of getting a negative prize.  (1 = every prize, 32000 = extremely rare)
Prize:DeathPrizeTime:::How long the prize exists that appears after killing somebody.
Prize:EngineShutdownTime:::Time the player is affected by an 'Engine Shutdown' Prize (in hundredth of a second)
Prize:TakePrizeReliable:0:1:Whether prize packets are sent reliably (C2S)
Prize:S2CTakePrizeReliable:0:1:Whether prize packets are sent reliably (S2C)
     */
    private EntityData ed;

    private Map<EntityId, Integer> spawnerPrizeCount = new HashMap<EntityId, Integer>();
    private EntitySet prizeSpawners;

    private HashMap<String, HashMap<String, Integer>> arenaPrizeWeights = new HashMap<>();
    private HashMap<String, HashMap<String, Integer>> arenaPrizeRules = new HashMap<>();

    private HashMap<String, Integer> arenaPrizeCounts = new HashMap<>();

    //private HashMap<String, Integer> prizeWeights = new HashMap<>();
    private HashMap<String, RandomSelector<String>> arenaSelectors = new HashMap<>();

    //RandomSelector<String> rc;
    Random random;
    private EntitySet prizes;
    private EntitySet ships;
    static Logger log = LoggerFactory.getLogger(PrizeState.class);
    private SimplePhysics simplePhysics;
    private SettingsState settings;
    private ArrayList<String> prizeWeightTypes = new ArrayList<>(), prizeRuleTypes = new ArrayList<>();
    private SimTime time;
    
    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);

        settings = getSystem(SettingsState.class);

        initPrizeRuleTypes();

        initPrizeWeightTypes();

        ComponentFilter prizeSpawnerFilter = FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.Prizes);

        prizeSpawners = ed.getEntities(prizeSpawnerFilter, Spawner.class, Position.class, SphereShape.class, ArenaId.class);

        //TODO: Read prize weights and load into random collection
        random = new Random();

        //HashMap<String, Integer> prizeWeightTests = this.loadTestingWeights();
        //RandomSelector testSelector = RandomSelector.weighted(prizeWeightTests.keySet(), s -> prizeWeightTests.get(s));
        //arenaSelectors.put(CoreGameConstants.DEFAULTARENAID, testSelector);
        ships = ed.getEntities(ArenaId.class, ShipType.class);
        prizes = ed.getEntities(ArenaId.class, PrizeType.class);

        this.simplePhysics = getSystem(SimplePhysics.class);
        this.simplePhysics.addCollisionListener(this);
    }

    private void initPrizeRuleTypes() {
        prizeRuleTypes = new ArrayList<>();

        prizeRuleTypes.add(PRIZE_DEATHPRIZETIME);
        prizeRuleTypes.add(PRIZE_ENGINESHUTDOWNTIME);
        prizeRuleTypes.add(PRIZE_MINIMUMVIRTUAL);
        prizeRuleTypes.add(PRIZE_MULTIPRIZECOUNT);
        prizeRuleTypes.add(PRIZE_PRIZEDELAY);
        prizeRuleTypes.add(PRIZE_PRIZEFACTOR);
        prizeRuleTypes.add(PRIZE_PRIZEHIDECOUNT);
        prizeRuleTypes.add(PRIZE_PRIZEMAXEXIST);
        prizeRuleTypes.add(PRIZE_PRIZEMINEXIST);
        prizeRuleTypes.add(PRIZE_PRIZENEGATIVEFACTOR);
        prizeRuleTypes.add(PRIZE_S2CTAKEPRIZERELIABLE);
        prizeRuleTypes.add(PRIZE_TAKEPRIZERELIABLE);
        prizeRuleTypes.add(PRIZE_UPGRADEVIRTUAL);
    }

    private HashMap<String, Integer> loadArenaPrizeWeights(String arenaId) {
        HashMap<String, Integer> newPrizeWeights = new HashMap<>();

        Ini arenaSettings = settings.getArenaSettings(arenaId);

        for (String s : prizeWeightTypes) {
            int weight = Integer.valueOf(arenaSettings.get(PRIZEWEIGHTSECTION, s));

            newPrizeWeights.put(s, weight);
        }

        return newPrizeWeights;
    }

    private HashMap<String, Integer> loadArenaPrizeRules(ArenaId arenaId) {
        HashMap<String, Integer> newPrizeRules = new HashMap<>();

        Ini arenaSettings = settings.getArenaSettings(arenaId.getArenaId());

        for (String s : prizeRuleTypes) {
            int weight = Integer.valueOf(arenaSettings.get(PRIZERULESECITON, s));

            newPrizeRules.put(s, weight);
        }

        return newPrizeRules;
    }

    private void initPrizeWeightTypes() {
        prizeWeightTypes = new ArrayList<>();

        prizeWeightTypes.add(PrizeTypes.ALLWEAPONS);
        prizeWeightTypes.add(PrizeTypes.ANTIWARP);
        prizeWeightTypes.add(PrizeTypes.BOMB);
        prizeWeightTypes.add(PrizeTypes.BOUNCINGBULLETS);
        prizeWeightTypes.add(PrizeTypes.BRICK);
        prizeWeightTypes.add(PrizeTypes.BURST);
        prizeWeightTypes.add(PrizeTypes.CLOAK);
        prizeWeightTypes.add(PrizeTypes.DECOY);
        prizeWeightTypes.add(PrizeTypes.ENERGY);
        prizeWeightTypes.add(PrizeTypes.ENERGY);
        prizeWeightTypes.add(PrizeTypes.GLUE);
        prizeWeightTypes.add(PrizeTypes.GUN);
        prizeWeightTypes.add(PrizeTypes.MULTIFIRE);
        prizeWeightTypes.add(PrizeTypes.MULTIPRIZE);
        prizeWeightTypes.add(PrizeTypes.PORTAL);
        prizeWeightTypes.add(PrizeTypes.PROXIMITY);
        prizeWeightTypes.add(PrizeTypes.QUICKCHARGE);
        prizeWeightTypes.add(PrizeTypes.RECHARGE);
        prizeWeightTypes.add(PrizeTypes.REPEL);
        prizeWeightTypes.add(PrizeTypes.ROCKET);
        prizeWeightTypes.add(PrizeTypes.ROTATION);
        prizeWeightTypes.add(PrizeTypes.SHIELDS);
        prizeWeightTypes.add(PrizeTypes.SHRAPNEL);
        prizeWeightTypes.add(PrizeTypes.STEALTH);
        prizeWeightTypes.add(PrizeTypes.THOR);
        prizeWeightTypes.add(PrizeTypes.THRUSTER);
        prizeWeightTypes.add(PrizeTypes.TOPSPEED);
        prizeWeightTypes.add(PrizeTypes.WARP);
        prizeWeightTypes.add(PrizeTypes.XRADAR);

    }

    /**
     * Load specific prize weights, used for testing
     */
    private HashMap<String, Integer> loadTestingWeights() {

        HashMap<String, Integer> prizeWeights = new HashMap<>();

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

        return prizeWeights;
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
        this.time = time;
        
        prizes.applyChanges();
        ships.applyChanges();

        prizeSpawners.applyChanges();

        //Make sure we have the arena prize weights and prize rules in place for new arenas:
        for (Entity e : prizeSpawners.getAddedEntities()) {
            ArenaId arena = e.get(ArenaId.class);

            if (!arenaPrizeWeights.containsKey(arena.getArenaId())) {
                HashMap<String, Integer> newPrizeWeights = this.loadArenaPrizeWeights(arena.getArenaId());
                arenaPrizeWeights.put(arena.getArenaId(), newPrizeWeights);

                RandomSelector newSelector = RandomSelector.weighted(newPrizeWeights.keySet(), s -> newPrizeWeights.get(s));
                arenaSelectors.put(arena.getArenaId(), newSelector);
            }

            if (!arenaPrizeRules.containsKey(arena.getArenaId())) {
                HashMap<String, Integer> newPrizeRules = this.loadArenaPrizeRules(arena);
                arenaPrizeRules.put(arena.getArenaId(), newPrizeRules);
            }
        }

        //TODO: Remove completely empty arenas
        /*
        for (Entity e : prizeSpawners.getRemovedEntities()) {
            ArenaId arena = e.get(ArenaId.class);

            if (arenaPrizeWeights.containsKey(arena.getArenaId())) {
                arenaPrizeWeights.remove(arena.getArenaId());
            }
            
            if (arenaPrizeRules.containsKey(arena.getArenaId())) {
                arenaPrizeRules.remove(arena.getArenaId());
            }
        }
         */
        //TODO: Need to account for prize rules
        for (Entity e : prizeSpawners) {
            ArenaId arena = e.get(ArenaId.class);

            HashMap<String, Integer> localArenaPrizeRules = arenaPrizeRules.get(arena.getArenaId());

            Spawner s = e.get(Spawner.class);
            Position p = e.get(Position.class);
            SphereShape c = e.get(SphereShape.class);
            if (spawnerPrizeCount.containsKey(e.getId()) && spawnerPrizeCount.get(e.getId()) < s.getMaxCount()) {
                spawnRandomWeightedPrize(arena.getArenaId(), p.getLocation(), c.getRadius(), true);
                spawnerPrizeCount.put(e.getId(), spawnerPrizeCount.get(e.getId()) + 1);
            } else {
                spawnRandomWeightedPrize(arena.getArenaId(), p.getLocation(), c.getRadius(), true);
                spawnerPrizeCount.put(e.getId(), 1);
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
    private EntityId spawnRandomWeightedPrize(String arenaId, Vec3d spawnCenter, double radius, boolean onlyCircumference) {
        Vec3d location = this.getRandomSpawnLocation(spawnCenter, radius, onlyCircumference);

        return ModuleGameEntities.createPrize(location, arenaSelectors.get(arenaId).next(random), ed, time.getTime());
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
            ModuleGameEntities.createSound(two, new Vec3d(loc.x, loc.y, 0), AudioTypes.PICKUP_PRIZE, ed, time.getTime());
            return false;
        } else if (prizes.containsId(two) && ships.containsId(one)) {
            PrizeType pt = prizes.getEntity(two).get(PrizeType.class);
            Vector2 loc = body2.getWorldCenter();
            this.handlePrizeAcquisition(pt, one);
            //Remove prize
            ed.removeEntity(two);
            //Play audio
            ModuleGameEntities.createSound(one, new Vec3d(loc.x, loc.y, 0), AudioTypes.PICKUP_PRIZE, ed, time.getTime());
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
        
        ArenaId arena = ed.getComponent(ship, ArenaId.class);
        String arenaName = arena.getArenaId();
        //Find settings
        Ini settings = this.settings.getArenaSettings(arenaName);
        
        
        log.info("Ship " + ship + " picked up prize:" + pt.getTypeName(ed));
        
        switch (pt.getTypeName(ed)) {
            case PrizeTypes.ALLWEAPONS: //PrizeWeight:AllWeapons:::Likelyhood of 'Super!' prize appearing
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.ANTIWARP:
                Antiwarp anti = ed.getComponent(ship, Antiwarp.class);
                if (anti == null) {
                    anti = new Antiwarp(false);
                    ed.setComponent(ship, anti);
                } 
                break;
            case PrizeTypes.BOMB:
                
            case PrizeTypes.BOUNCINGBULLETS:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.BRICK:
                throw new UnsupportedOperationException("Prize type: " + pt.getTypeName(ed) + " is not supported for pickup");
            case PrizeTypes.BURST:
                Burst burst = ed.getComponent(ship, Burst.class);
                if (burst == null) {
                    burst = new Burst(1);
                } else {
                    int count = burst.getCount();
                    burst = new Burst(count + 1);
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
                    t = new Thor(1);
                } else {
                    int count = t.getCount();
                    t = new Thor(count + 1);
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

    @Override
    public void arenaSettingsChange(ArenaId arenaId, String section, String setting) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
