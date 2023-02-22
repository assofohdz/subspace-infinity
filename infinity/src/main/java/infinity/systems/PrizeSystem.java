/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package infinity.systems;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.CollisionCategory;
import infinity.es.PrizeType;
import infinity.es.PrizeTypes;
import infinity.es.Spawner;
import infinity.es.SphereShape;
import infinity.es.ship.Player;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstMax;
import infinity.es.ship.weapons.Gun;
import infinity.es.ship.weapons.GunCost;
import infinity.es.ship.weapons.GunFireDelay;
import infinity.Guns;
import infinity.es.ship.weapons.GunMax;
import infinity.sim.CollisionFilters;
import infinity.sim.CoreGameConstants;
import infinity.sim.GameEntities;
import infinity.sim.GameSounds;
import infinity.util.RandomSelector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This system spawns prizes and handles prize acquisition.
 *
 * @author Asser
 */
public class PrizeSystem extends AbstractGameSystem implements ContactListener {

  static Logger log = LoggerFactory.getLogger(PrizeSystem.class);
  private final PhysicsSpace<EntityId, MBlockShape> phys;
  private final HashMap<EntityId, HashSet<EntityId>> spawnerBounties = new HashMap<>();
  private final HashMap<String, Integer> prizeWeights = new HashMap<>();
  private final HashMap<EntityId, Double> spawnerLastSpawned = new HashMap<>();
  BiMap<Integer, String> prizeMap = HashBiMap.create();
  RandomSelector<String> rc;
  Random random;
  private EntityData ed;
  private EntitySet prizeSpawners;
  private EntitySet prizes;
  private SimTime ourTime;
  private EntitySet ships;

  public PrizeSystem(PhysicsSpace<EntityId, MBlockShape> phys) {
    this.phys = phys;
  }

  @Override
  protected void initialize() {
    this.initializePrizeMap();

    ed = getSystem(EntityData.class);

    ComponentFilter prizeSpawnerFilter =
        FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.Prizes);

    prizeSpawners =
        ed.getEntities(prizeSpawnerFilter, Spawner.class, SpawnPosition.class, SphereShape.class);

    // TODO: Read prize weights and load into random collection
    random = new Random();

    this.loadPrizeWeights();

    rc = RandomSelector.weighted(prizeWeights.keySet(), prizeWeights::get);

    ComponentFilter shipColliderFilter =
        FieldFilter.create(
            CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS);
    ComponentFilter prizeColliderFilter =
        FieldFilter.create(
            CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS);
    // Can be updated later to include bots
    ships = ed.getEntities(shipColliderFilter, Player.class);
    prizes = ed.getEntities(prizeColliderFilter, PrizeType.class);

    getSystem(ContactSystem.class).addListener(this);
  }

  private void initializePrizeMap() {
    prizeMap.put(1, PrizeTypes.RECHARGE);
    prizeMap.put(2, PrizeTypes.ENERGY);
    prizeMap.put(3, PrizeTypes.ROTATION);
    prizeMap.put(4, PrizeTypes.STEALTH);
    prizeMap.put(5, PrizeTypes.CLOAK);
    prizeMap.put(6, PrizeTypes.XRADAR);
    prizeMap.put(7, PrizeTypes.WARP);
    prizeMap.put(8, PrizeTypes.GUN);
    prizeMap.put(9, PrizeTypes.BOMB);
    prizeMap.put(10, PrizeTypes.BOUNCINGBULLETS);
    prizeMap.put(11, PrizeTypes.THRUSTER);
    prizeMap.put(12, PrizeTypes.TOPSPEED);
    prizeMap.put(13, PrizeTypes.QUICKCHARGE);
    prizeMap.put(14, PrizeTypes.DUD);
    prizeMap.put(15, PrizeTypes.MULTIFIRE);
    prizeMap.put(16, PrizeTypes.PROXIMITY);
    prizeMap.put(17, PrizeTypes.SUPER);
    prizeMap.put(18, PrizeTypes.SHIELDS);
    prizeMap.put(19, PrizeTypes.SHRAPNEL);
    prizeMap.put(20, PrizeTypes.ANTIWARP);
    prizeMap.put(21, PrizeTypes.REPEL);
    prizeMap.put(22, PrizeTypes.BURST);
    prizeMap.put(23, PrizeTypes.DECOY);
    prizeMap.put(24, PrizeTypes.THOR);
    prizeMap.put(25, PrizeTypes.MULTIPRIZE);
    prizeMap.put(26, PrizeTypes.BRICK);
    prizeMap.put(27, PrizeTypes.ROCKET);
    prizeMap.put(28, PrizeTypes.PORTAL);
  }

  private void loadPrizeWeights() {
    prizeWeights.put(PrizeTypes.ALLWEAPONS, 5);
    prizeWeights.put(PrizeTypes.ANTIWARP, 10);
    prizeWeights.put(PrizeTypes.BOMB, 25);
    prizeWeights.put(PrizeTypes.BOUNCINGBULLETS, 5);
    prizeWeights.put(PrizeTypes.BRICK, 5);
    prizeWeights.put(PrizeTypes.BURST, 5);
    prizeWeights.put(PrizeTypes.CLOAK, 5);
    prizeWeights.put(PrizeTypes.DECOY, 5);
    prizeWeights.put(PrizeTypes.ENERGY, 5);
    prizeWeights.put(PrizeTypes.GLUE, 5);
    prizeWeights.put(PrizeTypes.GUN, 25);
    prizeWeights.put(PrizeTypes.MULTIFIRE, 5);
    prizeWeights.put(PrizeTypes.MULTIPRIZE, 5);
    prizeWeights.put(PrizeTypes.PORTAL, 5);
    prizeWeights.put(PrizeTypes.PROXIMITY, 5);
    prizeWeights.put(PrizeTypes.QUICKCHARGE, 5);
    prizeWeights.put(PrizeTypes.RECHARGE, 5);
    prizeWeights.put(PrizeTypes.REPEL, 5);
    prizeWeights.put(PrizeTypes.ROCKET, 5);
    prizeWeights.put(PrizeTypes.ROTATION, 5);
    prizeWeights.put(PrizeTypes.SHIELDS, 5);
    prizeWeights.put(PrizeTypes.SHRAPNEL, 5);
    prizeWeights.put(PrizeTypes.STEALTH, 5);
    prizeWeights.put(PrizeTypes.THOR, 5);
    prizeWeights.put(PrizeTypes.THRUSTER, 5);
    prizeWeights.put(PrizeTypes.TOPSPEED, 5);
    prizeWeights.put(PrizeTypes.WARP, 5);
    prizeWeights.put(PrizeTypes.XRADAR, 5);
  }

  @Override
  protected void terminate() {
    prizes.release();
    prizes = null;

    ships.release();
    ships = null;

    prizeSpawners.release();
    prizeSpawners = null;

    getSystem(ContactSystem.class).removeListener(this);
  }

  @Override
  public void update(SimTime time) {
    this.ourTime = time;

    prizes.applyChanges();
    ships.applyChanges();

    // Updated count if prizes are removed
    for (Entity bountyRemoved : prizes.getRemovedEntities()) {
      EntityId idBounty = bountyRemoved.getId();
      for (Entity entitySpawner : prizeSpawners) {
        HashSet<EntityId> spawnerBountySet = spawnerBounties.get(entitySpawner.getId());
        spawnerBountySet.remove(idBounty);
        spawnerBounties.put(entitySpawner.getId(), spawnerBountySet);
      }
    }

    prizeSpawners.applyChanges();

    for (Entity entitySpawner : prizeSpawners) { // Spawn max one per update-call / frame
      EntityId spawnerId = entitySpawner.getId();
      Spawner s = entitySpawner.get(Spawner.class);
      SpawnPosition p = entitySpawner.get(SpawnPosition.class);
      SphereShape c = entitySpawner.get(SphereShape.class);

      if (!spawnerBounties.containsKey(spawnerId)) {
        EntityId idBounty =
            spawnBounty(p.getLocation(), c.getRadius(), s.spawnOnRing(), s.isWeighted());

        HashSet<EntityId> spawnerBountySet = new HashSet<>();
        spawnerBountySet.add(idBounty);
        spawnerBounties.put(entitySpawner.getId(), spawnerBountySet);

        spawnerLastSpawned.put(entitySpawner.getId(), 0d);

      } else if (spawnerBounties.containsKey(spawnerId)
          && spawnerBounties.get(spawnerId).size() < s.getMaxCount()
          && spawnerLastSpawned.get(spawnerId) > s.getSpawnInterval()) {

        EntityId idBounty =
            spawnBounty(p.getLocation(), c.getRadius(), s.spawnOnRing(), s.isWeighted());

        spawnerLastSpawned.put(entitySpawner.getId(), 0d);

        HashSet<EntityId> spawnerBountySet = spawnerBounties.get(entitySpawner.getId());
        spawnerBountySet.add(idBounty);
        spawnerBounties.put(entitySpawner.getId(), spawnerBountySet);
      }

      spawnerLastSpawned.put(
          entitySpawner.getId(),
          spawnerLastSpawned.get(entitySpawner.getId()) + 1000 * time.getTpf());
    }
  }

  private EntityId spawnBounty(
      Vec3d spawnerLocation, double radius, boolean spawnOnRing, boolean weighted) {
    String prizeType = getPrizeType(weighted);
    Vec3d prizeSpawnLocation =
        this.getSpawnLocation(
            spawnerLocation, radius, spawnOnRing);

    return GameEntities.createPrize(ed, phys, ourTime.getTime(), prizeSpawnLocation, prizeType);
  }

  @Override
  public void start() {
    // Auto-generated method stub
  }

  @Override
  public void stop() {
    // Auto-generated method stub
  }

  private Vec3d getSpawnLocation(
      Vec3d spawnCenter, double radius, boolean onlyOnCircumference) {
    double angle = Math.random() * Math.PI * 2;

    double lengthFromCenter = onlyOnCircumference ? radius : radius * Math.random();

    double x = Math.cos(angle) * lengthFromCenter + spawnCenter.x;
    double z = Math.sin(angle) * lengthFromCenter + spawnCenter.z;

    return new Vec3d(x, 1, z);
  }

  private String getPrizeType(boolean weighted){
    return weighted ? rc.next(random) : prizeMap.get(ThreadLocalRandom.current().nextInt(1, 28 + 1));
  }

  private void handlePrizeAcquisition(PrizeType pt, EntityId ship) {
    log.info("Ship {} picked up prize: {}", ship, pt.getTypeName(ed));
    switch (pt.getTypeName(ed)) {
      case PrizeTypes.ALLWEAPONS:
        // TODO: Handle aquiring all weapons
        break;
      case PrizeTypes.ANTIWARP:
        // TODO: Handle acquiring antiwarp
        break;
      case PrizeTypes.BOMB:
        // TODO: Handle acquiring bomb
        break;
      case PrizeTypes.BOUNCINGBULLETS:
        // TODO: Handle acquiring bouncing bullets
        break;
      case PrizeTypes.BRICK:
        // TODO: Handle acquiring brick
        break;
      case PrizeTypes.BURST:
        handleAcquireBurst(ship);
        break;
      case PrizeTypes.CLOAK:
        // TODO: Handle acquiring cloak
        break;
      case PrizeTypes.DECOY:
        // TODO: Handle acquiring decoy
        break;
      case PrizeTypes.ENERGY:
        // TODO: Handle acquiring energy
        break;
      case PrizeTypes.GLUE:
        // TODO: Handle acquiring glue
        break;
      case PrizeTypes.GUN:
        handleAcquireGun(ship);
        break;
      case PrizeTypes.MULTIFIRE:
        // TODO: Handle acquiring multifire
        break;
      case PrizeTypes.MULTIPRIZE:
        // TODO: Handle acquiring multiprize
        break;
      case PrizeTypes.PORTAL:
        // TODO: Handle acquiring portal
        break;
      case PrizeTypes.PROXIMITY:
        // TODO: Handle acquiring proximity
        break;
      case PrizeTypes.QUICKCHARGE:
        // TODO: Handle acquiring quickcharge
        break;
      case PrizeTypes.RECHARGE:
        // TODO: Handle acquiring recharge
        break;
      case PrizeTypes.REPEL:
        // TODO: Handle acquiring repel
        break;
      case PrizeTypes.ROCKET:
        // TODO: Handle acquiring rocket
        break;
      case PrizeTypes.ROTATION:
        // TODO: Handle acquiring rotation
        break;
      case PrizeTypes.SHIELDS:
        // TODO: Handle acquiring shields
        break;
      case PrizeTypes.SHRAPNEL:
        // TODO: Handle acquiring shrapnel
        break;
      case PrizeTypes.STEALTH:
        // TODO: Handle acquiring stealth
        break;
      case PrizeTypes.THOR:
        // TODO: Handle acquiring thor
        break;
      case PrizeTypes.THRUSTER:
        // TODO: Handle acquiring thruster
        break;
      case PrizeTypes.TOPSPEED:
        // TODO: Handle acquiring topspeed
        break;
      case PrizeTypes.WARP:
        // TODO: Handle acquiring warp
        break;
      case PrizeTypes.XRADAR:
        // TODO: Handle acquiring xradar
        break;
      case PrizeTypes.SUPER:
        // TODO: Handle acquiring super
        break;
      case PrizeTypes.DUD:
        // TODO: Handle acquiring dud
        break;
      default:
        throw new UnsupportedOperationException(
            "Prize type: "
                + pt.getTypeName(ed)
                + " is not supported by "
                + pt.getClass().toString());
    }
  }

  private void handleAcquireBurst(EntityId ship) {
    Burst burst = ed.getComponent(ship, Burst.class);
    BurstMax burstMax = ed.getComponent(ship, BurstMax.class);
    if (burst != null && burstMax != null && burst.getCount() < burstMax.getCount()) {
      log.info("Ship {} picked up burst prize and now has {} bursts", ship, burst.getCount() + 1);
      ed.setComponent(ship, new Burst(burst.getCount() + 1));
    } else if (burst == null && burstMax != null) {
      log.info("Ship {} picked up burst prize", ship);
      ed.setComponent(ship, new Burst(1));
    }
  }

  private void handleAcquireGun(EntityId ship) {
    Gun gun = ed.getComponent(ship, Gun.class);
    GunMax max = ed.getComponent(ship, GunMax.class);
    if (gun != null && gun.getLevel().level < max.getLevel().level) {
      log.info("Gun level increased to {}", (gun.getLevel().level + 1));
      ed.setComponent(ship, new Gun(gun.getLevel().getNextLevel()));
    } else if (gun == null) {
      log.info("Ship {} just acquired guns and now has level {} guns", ship, 1);
      ed.setComponent(ship, new Gun(Guns.LEVEL_1));
      ed.setComponent(ship, new GunCost(CoreGameConstants.GUNCOST));
      ed.setComponent(ship, new GunFireDelay(CoreGameConstants.GUNCOOLDOWN));
      ed.setComponent(ship, new GunMax(Guns.LEVEL_4));
    }
  }

  @Override
  public void newContact(Contact contact) {
    RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    AbstractBody<EntityId, MBlockShape> body2 = contact.body2;


    if (body2 instanceof RigidBody) {
      EntityId idOne = body1.id;
      EntityId idTwo = body2.id;

      EntityId prizeId;
      EntityId shipId;

      // If one of the bodies is a ship and the other is a prize
      if (prizes.containsId(idTwo) && ships.containsId(idOne)) {
        prizeId = idTwo;
        shipId = idOne;
      } else if (prizes.containsId(idOne) && ships.containsId(idTwo)) {
        prizeId = idOne;
        shipId = idTwo;
      } else {
        return;
      }

      GameSounds.createPrizeSound(ed, ourTime.getTime(), shipId, body1.position, phys);

      PrizeType pt = prizes.getEntity(prizeId).get(PrizeType.class);
      this.handlePrizeAcquisition(pt, shipId);
      // Remove prize
      ed.removeEntity(prizeId);
      // Disable contact for further resolution
      contact.disable();
    }
  }
}
