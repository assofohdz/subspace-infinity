/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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

package infinity.systems;

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
import infinity.config.ShipConfig;
import infinity.es.CollisionCategory;
import infinity.es.PrizeType;
import infinity.es.PrizeTypes;
import infinity.es.PrizeWeightsOverride;
import infinity.es.Spawner;
import infinity.es.SphereShape;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyMax;
import infinity.es.ship.EnergyUpgrade;
import infinity.es.ship.Player;
import infinity.es.ship.Recharge;
import infinity.es.ship.ShipType;
import infinity.settings.ConfigRegistrySystem;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.RechargeUpgrade;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.RotationUpgrade;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.SpeedUpgrade;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;
import infinity.es.ship.ThrustUpgrade;
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
import infinity.sim.GameEntities;
import infinity.sim.GameSounds;
import infinity.util.RandomSelector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
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
  private final HashMap<EntityId, Double> spawnerLastSpawned = new HashMap<>();
  /**
   * Last-resort selector built from the hardcoded {@link #FALLBACK_WEIGHTS}.
   * Used only for spawners with no {@link ArenaId} (the legacy
   * {@code BasicEnvironment} hardcoded spawner) or when an arena has no
   * {@code [PrizeWeight]} keys at all.
   */
  RandomSelector<String> globalFallbackSelector;

  /**
   * Cached per-arena selector built lazily from
   * {@code SettingsSystem.getIni(arenaName).getSection("PrizeWeight")}. One
   * entry per arena seen so far.
   */
  private final HashMap<String, RandomSelector<String>> arenaSelectors = new HashMap<>();

  /**
   * Cached per-spawner selector built lazily for spawners carrying a
   * {@link PrizeWeightsOverride} component. Each entry merges the spawner's
   * arena defaults with the spawner-specific overrides at construction time;
   * dropped on spawner removal in {@link #update}.
   */
  private final HashMap<EntityId, RandomSelector<String>> spawnerSelectors = new HashMap<>();

  Random random;
  private EntityData ed;
  private ConfigRegistrySystem configRegistry;
  private SettingsSystem settingsSystem;
  private EntitySet prizeSpawners;
  private EntitySet prizes;
  private SimTime ourTime;
  private EntitySet ships;

  public PrizeSystem(PhysicsSpace<EntityId, MBlockShape> phys) {
    this.phys = phys;
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    configRegistry = getSystem(ConfigRegistrySystem.class);
    settingsSystem = getSystem(SettingsSystem.class);

    ComponentFilter<?> prizeSpawnerFilter =
        FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.Prizes);

    prizeSpawners =
        ed.getEntities(prizeSpawnerFilter, Spawner.class, SpawnPosition.class, SphereShape.class);

    random = new Random();
    globalFallbackSelector =
        RandomSelector.weighted(FALLBACK_WEIGHTS.keySet(), FALLBACK_WEIGHTS::get);

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

  /**
   * Last-resort weights used only when an arena has no {@code [PrizeWeight]}
   * keys configured AND the spawner doesn't carry a per-spawner override.
   * Mirrors the historical hardcoded distribution so existing behaviour for
   * the legacy {@code BasicEnvironment} spawner stays the same.
   */
  private static final java.util.Map<String, Integer> FALLBACK_WEIGHTS =
      java.util.Map.ofEntries(
          java.util.Map.entry(PrizeTypes.ALLWEAPONS, 5),
          java.util.Map.entry(PrizeTypes.ANTIWARP, 10),
          java.util.Map.entry(PrizeTypes.BOMB, 25),
          java.util.Map.entry(PrizeTypes.BOUNCINGBULLETS, 5),
          java.util.Map.entry(PrizeTypes.BRICK, 5),
          java.util.Map.entry(PrizeTypes.BURST, 5),
          java.util.Map.entry(PrizeTypes.CLOAK, 5),
          java.util.Map.entry(PrizeTypes.DECOY, 5),
          java.util.Map.entry(PrizeTypes.ENERGY, 5),
          java.util.Map.entry(PrizeTypes.GLUE, 5),
          java.util.Map.entry(PrizeTypes.GUN, 25),
          java.util.Map.entry(PrizeTypes.MULTIFIRE, 5),
          java.util.Map.entry(PrizeTypes.MULTIPRIZE, 5),
          java.util.Map.entry(PrizeTypes.PORTAL, 5),
          java.util.Map.entry(PrizeTypes.PROXIMITY, 5),
          java.util.Map.entry(PrizeTypes.QUICKCHARGE, 5),
          java.util.Map.entry(PrizeTypes.RECHARGE, 5),
          java.util.Map.entry(PrizeTypes.REPEL, 5),
          java.util.Map.entry(PrizeTypes.ROCKET, 5),
          java.util.Map.entry(PrizeTypes.ROTATION, 5),
          java.util.Map.entry(PrizeTypes.SHIELDS, 5),
          java.util.Map.entry(PrizeTypes.SHRAPNEL, 5),
          java.util.Map.entry(PrizeTypes.STEALTH, 5),
          java.util.Map.entry(PrizeTypes.THOR, 5),
          java.util.Map.entry(PrizeTypes.THRUSTER, 5),
          java.util.Map.entry(PrizeTypes.TOPSPEED, 5),
          java.util.Map.entry(PrizeTypes.WARP, 5),
          java.util.Map.entry(PrizeTypes.XRADAR, 5));

  /**
   * Lazily build (and cache) the {@link RandomSelector} for the given arena
   * by reading its {@code [PrizeWeight]} INI section via {@link
   * SettingsSystem}. Keys with weight {@code 0} are dropped (matches the
   * "weight 0 means never spawn" convention in the legacy fragments).
   *
   * @return the per-arena selector, or {@link #globalFallbackSelector} when
   *     the arena has no usable {@code [PrizeWeight]} entries
   */
  private RandomSelector<String> arenaSelector(final String arenaName) {
    final RandomSelector<String> cached = arenaSelectors.get(arenaName);
    if (cached != null) {
      return cached;
    }
    final java.util.Map<String, Integer> weights = readArenaWeights(arenaName);
    final RandomSelector<String> selector =
        weights.isEmpty()
            ? globalFallbackSelector
            : RandomSelector.weighted(weights.keySet(), weights::get);
    arenaSelectors.put(arenaName, selector);
    log.info(
        "PrizeWeight selector built for arena '{}' with {} non-zero entries",
        arenaName,
        weights.size());
    return selector;
  }

  private java.util.Map<String, Integer> readArenaWeights(final String arenaName) {
    final java.util.Map<String, Integer> weights = new HashMap<>();
    final org.ini4j.Ini ini = settingsSystem.getIni(arenaName);
    if (ini == null) {
      return weights;
    }
    final org.ini4j.Profile.Section section = ini.get("PrizeWeight");
    if (section == null) {
      return weights;
    }
    for (final String key : section.keySet()) {
      final String raw = section.get(key);
      if (raw == null) {
        continue;
      }
      try {
        final int w = Integer.parseInt(raw.trim());
        if (w > 0) {
          weights.put(key, w);
        }
      } catch (final NumberFormatException nfe) {
        log.warn("Arena '{}' [PrizeWeight] '{}' is non-numeric ({}); skipping", arenaName, key, raw);
      }
    }
    return weights;
  }

  /**
   * Build (and cache) a per-spawner selector by merging the spawner's arena
   * defaults with its {@link PrizeWeightsOverride} entries. Override entries
   * with {@code <= 0} weight are removed entirely so an arena default of
   * "Bomb=25" can be turned off by an override of "Bomb=0".
   */
  private RandomSelector<String> spawnerSelector(
      final EntityId spawnerId, final String arenaName, final PrizeWeightsOverride override) {
    final RandomSelector<String> cached = spawnerSelectors.get(spawnerId);
    if (cached != null) {
      return cached;
    }
    final java.util.Map<String, Integer> merged = new HashMap<>();
    if (arenaName != null) {
      merged.putAll(readArenaWeights(arenaName));
    }
    if (merged.isEmpty()) {
      merged.putAll(FALLBACK_WEIGHTS);
    }
    for (final java.util.Map.Entry<String, Integer> e : override.getOverrides().entrySet()) {
      if (e.getValue() == null || e.getValue() <= 0) {
        merged.remove(e.getKey());
      } else {
        merged.put(e.getKey(), e.getValue());
      }
    }
    final RandomSelector<String> selector =
        merged.isEmpty()
            ? globalFallbackSelector
            : RandomSelector.weighted(merged.keySet(), merged::get);
    spawnerSelectors.put(spawnerId, selector);
    log.info(
        "PrizeWeight selector built for spawner {} (arena='{}', override={}, merged={} entries)",
        spawnerId,
        arenaName,
        override.getOverrides(),
        merged.size());
    return selector;
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

    // Drop cached selectors for spawners that left the set (arena unload,
    // explicit despawn) so next-time-around builds reflect current settings.
    for (Entity removedSpawner : prizeSpawners.getRemovedEntities()) {
      spawnerSelectors.remove(removedSpawner.getId());
      spawnerBounties.remove(removedSpawner.getId());
      spawnerLastSpawned.remove(removedSpawner.getId());
    }

    for (Entity entitySpawner : prizeSpawners) { // Spawn max one per update-call / frame
      EntityId spawnerId = entitySpawner.getId();
      Spawner s = entitySpawner.get(Spawner.class);
      SpawnPosition p = entitySpawner.get(SpawnPosition.class);
      SphereShape c = entitySpawner.get(SphereShape.class);

      if (!spawnerBounties.containsKey(spawnerId)) {
        EntityId idBounty = spawnBounty(spawnerId, s, p.getLocation(), c.getRadius());

        HashSet<EntityId> spawnerBountySet = new HashSet<>();
        spawnerBountySet.add(idBounty);
        spawnerBounties.put(entitySpawner.getId(), spawnerBountySet);

        spawnerLastSpawned.put(entitySpawner.getId(), 0d);

      } else if (spawnerBounties.containsKey(spawnerId)
          && spawnerBounties.get(spawnerId).size() < s.getMaxCount()
          && spawnerLastSpawned.get(spawnerId) > s.getSpawnInterval()) {

        EntityId idBounty = spawnBounty(spawnerId, s, p.getLocation(), c.getRadius());

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

  /**
   * Spawn one prize for {@code spawner}. Reads the spawner's per-spawner
   * {@link Spawner#getSpawnedDecayMillis()} and forwards it to
   * {@code GameEntities.createPrize} so prizes from arena.groovy-declared
   * spawners can override the global {@code CoreGameConstants.PRIZEDECAY}.
   * Spawners created without a per-spawner TTL (e.g. the legacy
   * {@code BasicEnvironment} call) carry {@code 0} here, which
   * {@code createPrize} interprets as "fall back to the global default".
   *
   * <p>Prize-type weighting goes through {@link #getPrizeType(EntityId)}
   * which honours {@link PrizeWeightsOverride} on the spawner, falling back
   * to the spawner's arena defaults, then the global {@link #FALLBACK_WEIGHTS}.
   */
  private EntityId spawnBounty(
      EntityId spawnerId, Spawner spawner, Vec3d spawnerLocation, double radius) {
    String prizeType = getPrizeType(spawnerId);
    Vec3d prizeSpawnLocation =
        this.getSpawnLocation(spawnerLocation, radius, spawner.spawnOnRing());
    return GameEntities.createPrize(
        ed,
        phys,
        ourTime.getTime(),
        prizeSpawnLocation,
        prizeType,
        spawner.getSpawnedDecayMillis());
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

  /**
   * Pick a prize-type string for {@code spawner}'s next prize via the right
   * selector: per-spawner override > arena defaults > global fallback.
   */
  private String getPrizeType(EntityId spawnerId) {
    final PrizeWeightsOverride override = ed.getComponent(spawnerId, PrizeWeightsOverride.class);
    final ArenaId arenaId = ed.getComponent(spawnerId, ArenaId.class);
    final String arenaName = arenaId == null ? null : arenaId.getArena();
    if (override != null && !override.getOverrides().isEmpty()) {
      return spawnerSelector(spawnerId, arenaName, override).next(random);
    }
    if (arenaName != null) {
      return arenaSelector(arenaName).next(random);
    }
    return globalFallbackSelector.next(random);
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
        handleAcquireEnergy(ship);
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
        getSystem(EnergySystem.class).refillHealth(ship);
        break;
      case PrizeTypes.RECHARGE:
        handleAcquireRecharge(ship);
        break;
      case PrizeTypes.REPEL:
        // TODO: Handle acquiring repel
        break;
      case PrizeTypes.ROCKET:
        // TODO: Handle acquiring rocket
        break;
      case PrizeTypes.ROTATION:
        handleAcquireRotation(ship);
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
        handleAcquireThruster(ship);
        break;
      case PrizeTypes.TOPSPEED:
        handleAcquireTopSpeed(ship);
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
  /**
   * Look up the per-arena {@link ShipConfig} for {@code ship}'s current ship
   * type and arena membership. Returns {@code null} when either the
   * {@link ShipType} or {@link ArenaId} component is missing, or the arena
   * doesn't have a config for that ship type — callers log and skip in that
   * case (matches {@code ShipSpawnSystem}'s null-config handling).
   */
  private ShipConfig getShipConfig(EntityId ship) {
    final ShipType shipType = ed.getComponent(ship, ShipType.class);
    if (shipType == null || shipType.getType() == null) {
      return null;
    }
    final ArenaId arenaId = ed.getComponent(ship, ArenaId.class);
    if (arenaId == null) {
      return null;
    }
    return configRegistry.forArena(arenaId).getShip(shipType.getType());
  }

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
      // First-time mine acquisition: project starting level / max / cost / delay
      // from the per-arena ShipConfig (Pattern 4) instead of hardcoded constants.
      final ShipConfig cfg = getShipConfig(ship);
      if (cfg == null) {
        log.warn("Ship {} acquired mine prize without ShipConfig context; skipping", ship);
        return;
      }
      log.info("Ship {} picked up mine prize", ship);
      ed.setComponent(ship, new MineCurrentLevel(cfg.mines().start()));
      ed.setComponent(ship, new MineCost(cfg.mines().cost()));
      ed.setComponent(ship, new MineFireDelay(cfg.mines().fireDelayCs()));
      ed.setComponent(ship, new MineMaxLevel(cfg.mines().max()));
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
      // First-time bomb acquisition: project from per-arena ShipConfig.
      final ShipConfig cfg = getShipConfig(ship);
      if (cfg == null) {
        log.warn("Ship {} acquired bomb prize without ShipConfig context; skipping", ship);
        return;
      }
      log.info("Ship {} picked up bomb prize", ship);
      ed.setComponent(ship, new BombCurrentLevel(cfg.bombs().start()));
      ed.setComponent(ship, new BombCost(cfg.bombs().cost()));
      ed.setComponent(ship, new BombFireDelay(cfg.bombs().fireDelayCs()));
      ed.setComponent(ship, new BombMaxLevel(cfg.bombs().max()));
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
      // First-time gun acquisition: project from per-arena ShipConfig.
      final ShipConfig cfg = getShipConfig(ship);
      if (cfg == null) {
        log.warn("Ship {} acquired gun prize without ShipConfig context; skipping", ship);
        return;
      }
      log.info("Ship {} just acquired guns at level {}", ship, cfg.guns().start().level);
      ed.setComponent(ship, new GunCurrentLevel(cfg.guns().start()));
      ed.setComponent(ship, new GunCost(cfg.guns().cost()));
      ed.setComponent(ship, new GunFireDelay(cfg.guns().fireDelayCs()));
      ed.setComponent(ship, new GunMaxLevel(cfg.guns().max()));
    }
  }

  /**
   * THRUSTER prize: bumps the ship's current effective {@link Thrust} by
   * {@link ThrustUpgrade}, clamped at {@link ThrustMax}. No-op if the ship is
   * already at the cap, the upgrade increment is zero (e.g. trench preset's
   * "no upgrades" design), or the spawn projection hasn't run yet.
   */
  private void handleAcquireThruster(EntityId ship) {
    Thrust current = ed.getComponent(ship, Thrust.class);
    ThrustMax max = ed.getComponent(ship, ThrustMax.class);
    ThrustUpgrade up = ed.getComponent(ship, ThrustUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    int next = Math.min(current.getThrust() + up.getThrustUpgrade(), max.getThrustMax());
    if (next > current.getThrust()) {
      log.info("Ship {} thruster upgrade: thrust {} -> {}", ship, current.getThrust(), next);
      ed.setComponent(ship, new Thrust(next));
    }
  }

  /**
   * TOPSPEED prize: bumps the ship's current effective {@link Speed} by
   * {@link SpeedUpgrade}, clamped at {@link SpeedMax}.
   */
  private void handleAcquireTopSpeed(EntityId ship) {
    Speed current = ed.getComponent(ship, Speed.class);
    SpeedMax max = ed.getComponent(ship, SpeedMax.class);
    SpeedUpgrade up = ed.getComponent(ship, SpeedUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    int next = Math.min(current.getSpeed() + up.getSpeedUpgrade(), max.getSpeedMax());
    if (next > current.getSpeed()) {
      log.info("Ship {} topspeed upgrade: speed {} -> {}", ship, current.getSpeed(), next);
      ed.setComponent(ship, new Speed(next));
    }
  }

  /**
   * ROTATION prize: bumps the ship's current effective {@link Rotation} by
   * {@link RotationUpgrade}, clamped at {@link RotationMax}. All values are
   * in rad/sec (the Subspace integer rotation units are converted by
   * ShipSpawnSystem at spawn).
   */
  private void handleAcquireRotation(EntityId ship) {
    Rotation current = ed.getComponent(ship, Rotation.class);
    RotationMax max = ed.getComponent(ship, RotationMax.class);
    RotationUpgrade up = ed.getComponent(ship, RotationUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    double next = Math.min(current.getRadSec() + up.getRadSecUpgrade(), max.getRadSecMax());
    if (next > current.getRadSec()) {
      log.info("Ship {} rotation upgrade: rad/sec {} -> {}", ship, current.getRadSec(), next);
      ed.setComponent(ship, new Rotation(next));
    }
  }

  /**
   * ENERGY prize: bumps the ship's current effective energy cap {@link Energy}
   * by {@link EnergyUpgrade}, clamped at {@link EnergyMax}. Does <i>not</i>
   * touch the live pool — that's QUICKCHARGE's job (refills Health to Energy).
   */
  private void handleAcquireEnergy(EntityId ship) {
    Energy current = ed.getComponent(ship, Energy.class);
    EnergyMax max = ed.getComponent(ship, EnergyMax.class);
    EnergyUpgrade up = ed.getComponent(ship, EnergyUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    int next = Math.min(current.getEnergy() + up.getEnergyUpgrade(), max.getMaxEnergy());
    if (next > current.getEnergy()) {
      log.info("Ship {} energy upgrade: cap {} -> {}", ship, current.getEnergy(), next);
      ed.setComponent(ship, new Energy(next));
    }
  }

  /**
   * RECHARGE prize: bumps the ship's current effective {@link Recharge} rate
   * by {@link RechargeUpgrade}, clamped at {@link RechargeMax}. Values are in
   * energy-per-second (raw Subspace recharge units are converted by
   * ShipSpawnSystem at spawn).
   */
  private void handleAcquireRecharge(EntityId ship) {
    Recharge current = ed.getComponent(ship, Recharge.class);
    RechargeMax max = ed.getComponent(ship, RechargeMax.class);
    RechargeUpgrade up = ed.getComponent(ship, RechargeUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    double next = Math.min(
        current.getRechargePerSecond() + up.getRechargePerSecondUpgrade(),
        max.getMaxRechargePerSecond());
    if (next > current.getRechargePerSecond()) {
      log.info(
          "Ship {} recharge upgrade: energy/sec {} -> {}",
          ship, current.getRechargePerSecond(), next);
      ed.setComponent(ship, new Recharge(next));
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
