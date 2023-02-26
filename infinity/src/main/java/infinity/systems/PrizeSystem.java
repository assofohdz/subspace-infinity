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
import infinity.Bombs;
import infinity.Guns;
import infinity.es.CollisionCategory;
import infinity.es.PrizeType;
import infinity.es.PrizeTypes;
import infinity.es.Spawner;
import infinity.es.SphereShape;
import infinity.es.ship.Player;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstMax;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorMaxCount;
import infinity.es.ship.weapons.BombCost;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombMaxLevel;
import infinity.es.ship.weapons.GunCost;
import infinity.es.ship.weapons.GunCurrentLevel;
import infinity.es.ship.weapons.GunFireDelay;
import infinity.es.ship.weapons.GunMaxLevel;
import infinity.es.ship.weapons.MineCost;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineMaxLevel;
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
public class PrizeSystem extends AbstractGameSystem implements ContactListener<EntityId, MBlockShape> {

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

    ComponentFilter<?> prizeSpawnerFilter =
        FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.Prizes);

    prizeSpawners =
        ed.getEntities(prizeSpawnerFilter, Spawner.class, SpawnPosition.class, SphereShape.class);

    // TODO: Read prize weights and load into random collection
    random = new Random();

    this.loadPrizeWeights();

    rc = RandomSelector.weighted(prizeWeights.keySet(), prizeWeights::get);

    ComponentFilter<?> shipColliderFilter =
        FieldFilter.create(
            CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS);
    ComponentFilter<?> prizeColliderFilter =
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
    Vec3d prizeSpawnLocation = this.getSpawnLocation(spawnerLocation, radius, spawnOnRing);

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

  private Vec3d getSpawnLocation(Vec3d spawnCenter, double radius, boolean onlyOnCircumference) {
    double angle = Math.random() * Math.PI * 2;

    double lengthFromCenter = onlyOnCircumference ? radius : radius * Math.random();

    double x = Math.cos(angle) * lengthFromCenter + spawnCenter.x;
    double z = Math.sin(angle) * lengthFromCenter + spawnCenter.z;

    return new Vec3d(x, 1, z);
  }

  private String getPrizeType(boolean weighted) {
    return weighted
        ? rc.next(random)
        : prizeMap.get(ThreadLocalRandom.current().nextInt(1, 28 + 1));
  }

  private void handlePrizeAcquisition(PrizeType pt, EntityId ship) {
    log.info("Ship {} picked up prize: {}", ship, pt.getTypeName(ed));
    switch (pt.getTypeName(ed)) {
      case PrizeTypes.ALLWEAPONS:
        handleAcquireBomb(ship);
        handleAcquireBurst(ship);
        handleAcquireGun(ship);
        handleAcquireMine(ship);
        break;
      case PrizeTypes.ANTIWARP:
        // TODO: Handle acquiring antiwarp
        break;
      case PrizeTypes.BOMB:
        handleAcquireBomb(ship);
        handleAcquireMine(ship);
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
        handleAcquireThor(ship);
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

  private void handleAcquireThor(EntityId ship) {
    ThorCurrentCount thorCurrentCount = ed.getComponent(ship, ThorCurrentCount.class);
    ThorMaxCount thorMaxCount = ed.getComponent(ship, ThorMaxCount.class);
    if (thorCurrentCount != null && thorCurrentCount.getCount() < thorMaxCount.getCount()) {
      ThorCurrentCount thorNextCount = thorCurrentCount.add(1);
      log.info(
          "Ship {} picked up thor prize and now has {} thor", ship, (thorNextCount.getCount()));
      ed.setComponent(ship, thorNextCount);
    } else if (thorMaxCount != null) {
      log.info("Ship {} picked up thor prize", ship);
      ed.setComponent(ship, new ThorCurrentCount(1));
      ed.setComponent(ship, new ThorFireDelay(1000));
    }
  }

  /**
   * This method handles upgrading or acquiring mines. This happens when a ship picks up a bomb
   * prize. The ship will either acquire a mine if it does not have one, or upgrade its mine if it
   * already has one. If the ship already has the maximum mine, nothing happens.
   *
   * <p>Note: A bomb prize also acts a mine prize.
   *
   * @param ship The ship that picked up the bomb prize.
   */
  private void handleAcquireMine(EntityId ship) {
    MineCurrentLevel mineCurrentLevel = ed.getComponent(ship, MineCurrentLevel.class);
    MineMaxLevel mineMaxLevel = ed.getComponent(ship, MineMaxLevel.class);
    if (mineCurrentLevel != null
        && mineCurrentLevel.getLevel().level < mineMaxLevel.getLevel().level) {
      log.info(
          "Ship {} picked up mine prize and now has {} mines",
          ship,
          (mineCurrentLevel.getLevel().next()));
      ed.setComponent(ship, new MineCurrentLevel(mineCurrentLevel.getLevel().next()));
    } else if (mineCurrentLevel == null && mineMaxLevel != null) {
      log.info("Ship {} picked up mine prize", ship);
      ed.setComponent(ship, new MineCurrentLevel(Bombs.BOMB_1));
      ed.setComponent(ship, new MineCost(CoreGameConstants.MINECOST));
      ed.setComponent(ship, new MineFireDelay(CoreGameConstants.MINECOOLDOWN));
      ed.setComponent(ship, new MineMaxLevel(Bombs.BOMB_4));
    }
  }

  private void handleAcquireBomb(EntityId ship) {
    BombCurrentLevel bombCurrentLevel = ed.getComponent(ship, BombCurrentLevel.class);
    BombMaxLevel bombMaxLevel = ed.getComponent(ship, BombMaxLevel.class);
    if (bombCurrentLevel != null
        && bombCurrentLevel.getLevel().level < bombMaxLevel.getLevel().level) {
      log.info(
          "Ship {} picked up bomb prize and now has {} bombs",
          ship,
          (bombCurrentLevel.getLevel().next()));
      ed.setComponent(ship, new BombCurrentLevel(bombCurrentLevel.getLevel().next()));
    } else if (bombCurrentLevel == null && bombMaxLevel != null) {
      log.info("Ship {} picked up bomb prize", ship);
      ed.setComponent(ship, new BombCurrentLevel(Bombs.BOMB_1));
      ed.setComponent(ship, new BombCost(CoreGameConstants.BOMBCOST));
      ed.setComponent(ship, new BombFireDelay(CoreGameConstants.BOMBCOOLDOWN));
      ed.setComponent(ship, new BombMaxLevel(Bombs.BOMB_4));
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
    GunCurrentLevel gunCurrentLevel = ed.getComponent(ship, GunCurrentLevel.class);
    GunMaxLevel max = ed.getComponent(ship, GunMaxLevel.class);
    if (gunCurrentLevel != null && gunCurrentLevel.getLevel().level < max.getLevel().level) {
      log.info("Gun level increased to {}", (gunCurrentLevel.getLevel().next()));
      ed.setComponent(ship, new GunCurrentLevel(gunCurrentLevel.getLevel().next()));
    } else if (gunCurrentLevel == null) {
      log.info("Ship {} just acquired guns and now has level {} guns", ship, 1);
      ed.setComponent(ship, new GunCurrentLevel(Guns.LEVEL_1));
      ed.setComponent(ship, new GunCost(CoreGameConstants.GUNCOST));
      ed.setComponent(ship, new GunFireDelay(CoreGameConstants.GUNCOOLDOWN));
      ed.setComponent(ship, new GunMaxLevel(Guns.LEVEL_4));
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
