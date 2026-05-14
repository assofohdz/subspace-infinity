// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.es.CollisionCategory;
import infinity.es.PrizeType;
import infinity.es.PrizeTypes;
import infinity.es.PrizeWeightsOverride;
import infinity.es.Spawner;
import infinity.es.SphereShape;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Player;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import infinity.sim.CollisionFilters;
import infinity.sim.MapFactory;
import infinity.util.RandomSelector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Periodic prize spawner — drives per-{@link Spawner} cadence + additive player-count scaling. See ADR 0001 / 0003. */
public class PrizeSpawnerSystem extends BaseInfinitySystem {

  static Logger log = LoggerFactory.getLogger(PrizeSpawnerSystem.class);

  /**
   * Last-resort weights used only when an arena has no {@code [PrizeWeight]}
   * keys configured AND the spawner doesn't carry a per-spawner override.
   * Mirrors the historical hardcoded distribution so existing behaviour for
   * the legacy {@code BasicEnvironment} spawner stays the same.
   */
  private static final Map<String, Integer> FALLBACK_WEIGHTS =
      Map.ofEntries(
          Map.entry(PrizeTypes.ALLWEAPONS, 5),
          Map.entry(PrizeTypes.ANTIWARP, 10),
          Map.entry(PrizeTypes.BOMB, 25),
          Map.entry(PrizeTypes.BOUNCINGBULLETS, 5),
          Map.entry(PrizeTypes.BRICK, 5),
          Map.entry(PrizeTypes.BURST, 5),
          Map.entry(PrizeTypes.CLOAK, 5),
          Map.entry(PrizeTypes.DECOY, 5),
          Map.entry(PrizeTypes.ENERGY, 5),
          Map.entry(PrizeTypes.GLUE, 5),
          Map.entry(PrizeTypes.GUN, 25),
          Map.entry(PrizeTypes.MULTIFIRE, 5),
          Map.entry(PrizeTypes.MULTIPRIZE, 5),
          Map.entry(PrizeTypes.PORTAL, 5),
          Map.entry(PrizeTypes.PROXIMITY, 5),
          Map.entry(PrizeTypes.QUICKCHARGE, 5),
          Map.entry(PrizeTypes.RECHARGE, 5),
          Map.entry(PrizeTypes.REPEL, 5),
          Map.entry(PrizeTypes.ROCKET, 5),
          Map.entry(PrizeTypes.ROTATION, 5),
          Map.entry(PrizeTypes.SHIELDS, 5),
          Map.entry(PrizeTypes.SHRAPNEL, 5),
          Map.entry(PrizeTypes.STEALTH, 5),
          Map.entry(PrizeTypes.THOR, 5),
          Map.entry(PrizeTypes.THRUSTER, 5),
          Map.entry(PrizeTypes.TOPSPEED, 5),
          Map.entry(PrizeTypes.WARP, 5),
          Map.entry(PrizeTypes.XRADAR, 5));

  private final PhysicsSpace<EntityId, MBlockShape> phys;
  private final Map<EntityId, Set<EntityId>> spawnerBounties = new HashMap<>();
  private final Map<EntityId, Double> spawnerLastSpawned = new HashMap<>();
  private final Map<String, RandomSelector<String>> arenaSelectors = new HashMap<>();
  private final Map<EntityId, RandomSelector<String>> spawnerSelectors = new HashMap<>();

  private RandomSelector<String> globalFallbackSelector;
  private Random random;
  private EntityData ed;
  private ConfigRegistrySystem configRegistry;
  private EngineConfigSystem engineConfigSystem;
  private EntitySet prizeSpawners;
  private EntitySet prizes;
  private EntitySet ships;
  private SimTime ourTime;

  public PrizeSpawnerSystem(final PhysicsSpace<EntityId, MBlockShape> phys) {
    this.phys = phys;
  }

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);

    final ComponentFilter<?> prizeSpawnerFilter =
        FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.PRIZES);
    prizeSpawners =
        ed.getEntities(prizeSpawnerFilter, Spawner.class, SpawnPosition.class, SphereShape.class);

    final ComponentFilter<?> shipColliderFilter =
        FieldFilter.create(
            CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS);
    final ComponentFilter<?> prizeColliderFilter =
        FieldFilter.create(
            CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS);
    ships = ed.getEntities(shipColliderFilter, Player.class);
    prizes = ed.getEntities(prizeColliderFilter, PrizeType.class);

    random = new Random();
    globalFallbackSelector =
        RandomSelector.weighted(FALLBACK_WEIGHTS.keySet(), FALLBACK_WEIGHTS::get);
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
  public void start() {
    // no-op
  }

  @Override
  public void stop() {
    // no-op
  }

  @Override
  public void update(final SimTime time) {
    this.ourTime = time;

    prizes.applyChanges();
    ships.applyChanges();

    pruneRemovedPrizes();

    prizeSpawners.applyChanges();

    pruneRemovedSpawners();

    // Hoist ship-id collection — one EntitySet walk per tick, not per spawner.
    final List<EntityId> shipIds = new ArrayList<>(ships.size());
    for (final Entity ship : ships) {
      shipIds.add(ship.getId());
    }

    for (final Entity entitySpawner : prizeSpawners) {
      final EntityId spawnerId = entitySpawner.getId();
      final Spawner s = entitySpawner.get(Spawner.class);
      final SpawnPosition p = entitySpawner.get(SpawnPosition.class);
      final SphereShape c = entitySpawner.get(SphereShape.class);

      final ArenaId spawnerArenaId = ed.getComponent(spawnerId, ArenaId.class);
      final int playersInArena = countPlayersInArena(ed, shipIds, spawnerArenaId);
      final int effectiveMaxCount =
          computeEffectiveMaxCount(s.getMaxCount(), s.getCountPerPlayer(), playersInArena);
      final double effectiveRadius =
          computeEffectiveRadius(c.getRadius(), s.getRadiusPerPlayer(), playersInArena);

      Set<EntityId> spawnerBountySet = spawnerBounties.get(spawnerId);
      if (spawnerBountySet == null) {
        spawnerBountySet = new HashSet<>();
        final EntityId idBounty = spawnBounty(spawnerId, s, p.getLocation(), effectiveRadius);
        spawnerBountySet.add(idBounty);
        spawnerBounties.put(spawnerId, spawnerBountySet);
        spawnerLastSpawned.put(spawnerId, 0d);
      } else if (spawnerBountySet.size() < effectiveMaxCount
          && spawnerLastSpawned.get(spawnerId) > s.getSpawnInterval()) {
        final int batch =
            computeRegenAmount(s.getRegenBatch(), spawnerBountySet.size(), effectiveMaxCount);
        for (int i = 0; i < batch; i++) {
          final EntityId idBounty = spawnBounty(spawnerId, s, p.getLocation(), effectiveRadius);
          spawnerBountySet.add(idBounty);
        }
        spawnerLastSpawned.put(spawnerId, 0d);
      }

      spawnerLastSpawned.put(spawnerId, spawnerLastSpawned.get(spawnerId) + 1000 * time.getTpf());
    }
  }

  private void pruneRemovedPrizes() {
    for (final Entity bountyRemoved : prizes.getRemovedEntities()) {
      final EntityId idBounty = bountyRemoved.getId();
      for (final Entity entitySpawner : prizeSpawners) {
        final Set<EntityId> spawnerBountySet = spawnerBounties.get(entitySpawner.getId());
        spawnerBountySet.remove(idBounty);
        spawnerBounties.put(entitySpawner.getId(), spawnerBountySet);
      }
    }
  }

  private void pruneRemovedSpawners() {
    for (final Entity removedSpawner : prizeSpawners.getRemovedEntities()) {
      spawnerSelectors.remove(removedSpawner.getId());
      spawnerBounties.remove(removedSpawner.getId());
      spawnerLastSpawned.remove(removedSpawner.getId());
    }
  }

  /** Effective cap = {@code baseMax + countPerPlayer × playerCount}; collapses to base on either 0. */
  static int computeEffectiveMaxCount(
      final int baseMax, final int countPerPlayer, final int playerCount) {
    return baseMax + countPerPlayer * playerCount;
  }

  /** Effective radius = {@code baseRadius + radiusPerPlayer × playerCount}. */
  static double computeEffectiveRadius(
      final double baseRadius, final double radiusPerPlayer, final int playerCount) {
    return baseRadius + radiusPerPlayer * playerCount;
  }

  /** Deficit-capped regen batch; misconfigured {@code regenBatch < 1} clamps to 1 so spawners trickle. */
  static int computeRegenAmount(
      final int regenBatch, final int currentAlive, final int effectiveMaxCount) {
    final int deficit = effectiveMaxCount - currentAlive;
    if (deficit <= 0) {
      return 0;
    }
    return Math.min(Math.max(regenBatch, 1), deficit);
  }

  /** Count active ships whose {@link ArenaId} matches {@code targetArena}; null arena → 0 (legacy spawners). */
  static int countPlayersInArena(
      final EntityData ed, final Iterable<EntityId> shipIds, final ArenaId targetArena) {
    if (targetArena == null) {
      return 0;
    }
    final String arenaName = targetArena.getArena();
    int count = 0;
    for (final EntityId shipId : shipIds) {
      final ArenaId shipArenaId = ed.getComponent(shipId, ArenaId.class);
      if (shipArenaId != null && arenaName.equals(shipArenaId.getArena())) {
        count++;
      }
    }
    return count;
  }

  private EntityId spawnBounty(
      final EntityId spawnerId,
      final Spawner spawner,
      final Vec3d spawnerLocation,
      final double radius) {
    String prizeType = getPrizeType(spawnerId);
    final ArenaId arenaId = ed.getComponent(spawnerId, ArenaId.class);
    final infinity.config.PrizeConfig prize =
        arenaId == null
            ? infinity.config.PrizeConfig.DEFAULTS
            : configRegistry.forArena(arenaId).prize();
    prizeType = maybeRollNegative(prizeType, prize);
    final Vec3d prizeSpawnLocation =
        getSpawnLocation(spawnerLocation, radius, spawner.spawnOnRing());
    final long decayMs = resolveDecayMs(spawnerId, spawner);
    return MapFactory.createPrize(
        ed,
        new infinity.sim.specs.PrizeArgs(
            phys,
            ourTime.getTime(),
            prizeSpawnLocation,
            prizeType,
            decayMs,
            spawner.isHidden(),
            engineConfigSystem.get().prizeRadius()));
  }

  /** Subspace 1-in-N negative-prize roll → substitutes {@link PrizeTypes#DUD}. REFERENCE.md {@code ## Prize / PrizeNegativeFactor}. */
  String maybeRollNegative(final String prizeType, final infinity.config.PrizeConfig prize) {
    final int factor = prize.prizeNegativeFactor();
    if (factor <= 0 || PrizeTypes.DUD.equals(prizeType)) {
      return prizeType;
    }
    if (random.nextInt(factor) == 0) {
      log.info("Negative-prize roll hit (1 in {}): {} → Dud", factor, prizeType);
      return PrizeTypes.DUD;
    }
    return prizeType;
  }

  private long resolveDecayMs(final EntityId spawnerId, final Spawner spawner) {
    if (spawner.getSpawnedDecayMillis() > 0L) {
      // Per-spawner ttlMs is a fixed override — explicit author intent wins, no randomisation.
      return spawner.getSpawnedDecayMillis();
    }
    final ArenaId arenaId = ed.getComponent(spawnerId, ArenaId.class);
    final infinity.config.PrizeConfig prize =
        arenaId == null
            ? infinity.config.PrizeConfig.DEFAULTS
            : configRegistry.forArena(arenaId).prize();
    return sampleDecayMs(prize);
  }

  private long sampleDecayMs(final infinity.config.PrizeConfig prize) {
    final long min = prize.defaultMinDecayMs();
    final long max = prize.defaultDecayMs();
    if (min >= max) {
      return max;
    }
    return random.nextLong(min, max + 1);
  }

  private Vec3d getSpawnLocation(
      final Vec3d spawnCenter, final double radius, final boolean onlyOnCircumference) {
    final double angle = Math.random() * Math.PI * 2;
    final double lengthFromCenter = onlyOnCircumference ? radius : radius * Math.random();
    final double x = Math.cos(angle) * lengthFromCenter + spawnCenter.x;
    final double z = Math.sin(angle) * lengthFromCenter + spawnCenter.z;
    return new Vec3d(x, 1, z);
  }

  private RandomSelector<String> arenaSelector(final String arenaName) {
    final RandomSelector<String> cached = arenaSelectors.get(arenaName);
    if (cached != null) {
      return cached;
    }
    final Map<String, Integer> weights = readArenaWeights(arenaName);
    final RandomSelector<String> selector =
        weights.isEmpty()
            ? globalFallbackSelector
            : RandomSelector.weighted(weights.keySet(), weights::get);
    arenaSelectors.put(arenaName, selector);
    if (log.isInfoEnabled()) {
      log.info(
          "PrizeWeight selector built for arena '{}' with {} non-zero entries",
          arenaName,
          weights.size());
    }
    return selector;
  }

  private Map<String, Integer> readArenaWeights(final String arenaName) {
    final ArenaId arenaId = new ArenaId(arenaName, EntityId.NULL_ID);
    final Map<String, Integer> source =
        configRegistry.forArena(arenaId).prizeWeights().weights();
    final Map<String, Integer> weights = new HashMap<>();
    for (final Map.Entry<String, Integer> entry : source.entrySet()) {
      if (entry.getValue() > 0) {
        weights.put(entry.getKey(), entry.getValue());
      }
    }
    return weights;
  }

  private RandomSelector<String> spawnerSelector(
      final EntityId spawnerId, final String arenaName, final PrizeWeightsOverride override) {
    final RandomSelector<String> cached = spawnerSelectors.get(spawnerId);
    if (cached != null) {
      return cached;
    }
    final Map<String, Integer> merged = new HashMap<>();
    if (arenaName != null) {
      merged.putAll(readArenaWeights(arenaName));
    }
    if (merged.isEmpty()) {
      merged.putAll(FALLBACK_WEIGHTS);
    }
    for (final Map.Entry<String, Integer> e : override.getOverrides().entrySet()) {
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
    if (log.isInfoEnabled()) {
      log.info(
          "PrizeWeight selector built for spawner {} (arena='{}', override={}, merged={} entries)",
          spawnerId,
          arenaName,
          override.getOverrides(),
          merged.size());
    }
    return selector;
  }

  private String getPrizeType(final EntityId spawnerId) {
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

  /**
   * Picks an arena-weighted prize type for death-drops. Shared with
   * {@link DeathPrizeSystem} so the same per-arena selector cache + negative-
   * roll logic is used regardless of whether the prize was scheduled by a
   * spawner or by a death-edge intent.
   */
  String pickPrizeTypeForArena(final String arenaName, final infinity.config.PrizeConfig prize) {
    final String selected =
        arenaName == null
            ? globalFallbackSelector.next(random)
            : arenaSelector(arenaName).next(random);
    return maybeRollNegative(selected, prize);
  }
}
