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
import infinity.es.PrizeWeightsOverride;
import infinity.es.Spawner;
import infinity.es.SphereShape;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Player;
import infinity.settings.ConfigRegistrySystem;
import infinity.sim.CollisionFilters;
import infinity.sim.GameEntities;
import infinity.sim.GameSounds;
import infinity.systems.ship.EnergySystem;
import infinity.systems.ship.WarpSystem;
import infinity.systems.ship.applier.AntiWarpPrizeApplier;
import infinity.systems.ship.applier.BombPrizeApplier;
import infinity.systems.ship.applier.BouncingBulletsPrizeApplier;
import infinity.systems.ship.applier.BrickPrizeApplier;
import infinity.systems.ship.applier.BurstPrizeApplier;
import infinity.systems.ship.applier.CloakPrizeApplier;
import infinity.systems.ship.applier.CompositePrizeApplier;
import infinity.systems.ship.applier.DecoyPrizeApplier;
import infinity.systems.ship.applier.DudPrizeApplier;
import infinity.systems.ship.applier.EnergyPrizeApplier;
import infinity.systems.ship.applier.GluePrizeApplier;
import infinity.systems.ship.applier.GunPrizeApplier;
import infinity.systems.ship.applier.MinePrizeApplier;
import infinity.systems.ship.applier.MultiFirePrizeApplier;
import infinity.systems.ship.applier.MultiPrizePrizeApplier;
import infinity.systems.ship.applier.PortalPrizeApplier;
import infinity.systems.ship.applier.PrizeApplier;
import infinity.systems.ship.applier.PrizeApplierContext;
import infinity.systems.ship.applier.ProximityPrizeApplier;
import infinity.systems.ship.applier.QuickChargePrizeApplier;
import infinity.systems.ship.applier.RechargePrizeApplier;
import infinity.systems.ship.applier.RepelPrizeApplier;
import infinity.systems.ship.applier.RocketPrizeApplier;
import infinity.systems.ship.applier.RotationPrizeApplier;
import infinity.systems.ship.applier.ShieldsPrizeApplier;
import infinity.systems.ship.applier.ShrapnelPrizeApplier;
import infinity.systems.ship.applier.StealthPrizeApplier;
import infinity.systems.ship.applier.SuperPrizeApplier;
import infinity.systems.ship.applier.ThorPrizeApplier;
import infinity.systems.ship.applier.ThrusterPrizeApplier;
import infinity.systems.ship.applier.TopSpeedPrizeApplier;
import infinity.systems.ship.applier.WarpPrizeApplier;
import infinity.systems.ship.applier.XRadarPrizeApplier;
import infinity.util.RandomSelector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
  private final Map<EntityId, Set<EntityId>> spawnerBounties = new HashMap<>();
  private final Map<EntityId, Double> spawnerLastSpawned = new HashMap<>();
  /**
   * Last-resort selector built from the hardcoded {@link #FALLBACK_WEIGHTS}.
   * Used only for spawners with no {@link ArenaId} (the legacy
   * {@code BasicEnvironment} hardcoded spawner) or when an arena has no
   * {@code [PrizeWeight]} keys at all.
   */
  RandomSelector<String> globalFallbackSelector;

  /**
   * Cached per-arena selector built lazily from
   * {@code ConfigRegistrySystem.forArena(arenaId).prizeWeights()} (typed
   * via {@code PrizeWeightsAdapter}). One entry per arena seen so far.
   */
  private final Map<String, RandomSelector<String>> arenaSelectors = new HashMap<>();

  /**
   * Cached per-spawner selector built lazily for spawners carrying a
   * {@link PrizeWeightsOverride} component. Each entry merges the spawner's
   * arena defaults with the spawner-specific overrides at construction time;
   * dropped on spawner removal in {@link #update}.
   */
  private final Map<EntityId, RandomSelector<String>> spawnerSelectors = new HashMap<>();

  Random random;
  private EntityData ed;
  private ConfigRegistrySystem configRegistry;
  /**
   * Registry of prize-type-name → applier. Built once in {@link #initialize()}
   * — one entry per Subspace prize type, with composite appliers wired for
   * {@code BOMB} (bomb+mine) and {@code ALLWEAPONS} (bomb+burst+bullet+mine).
   * Stub appliers throw {@link UnsupportedOperationException}; the dispatch
   * in {@link #handlePrizeAcquisition} catches that and logs a warning so
   * unimplemented prize types degrade to a visible no-op rather than
   * crashing the contact loop.
   */
  private java.util.Map<String, PrizeApplier> appliers;
  private PrizeApplierContext applierContext;
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

    // Build the prize-applier registry. Composites for BOMB (bomb+mine)
    // and ALLWEAPONS (bomb+burst+bullet+mine) — Subspace tradition.
    final EnergySystem energySystem = getSystem(EnergySystem.class);
    final WarpSystem warpSystem = getSystem(WarpSystem.class);
    applierContext = new PrizeApplierContext(ed, energySystem, warpSystem);
    final BombPrizeApplier bomb = new BombPrizeApplier();
    final BurstPrizeApplier burst = new BurstPrizeApplier();
    final GunPrizeApplier bullet = new GunPrizeApplier();
    final MinePrizeApplier mine = new MinePrizeApplier();
    appliers = new HashMap<>();
    appliers.put(PrizeTypes.ALLWEAPONS, new CompositePrizeApplier(bomb, burst, bullet, mine));
    appliers.put(PrizeTypes.ANTIWARP, new AntiWarpPrizeApplier());
    appliers.put(PrizeTypes.BOMB, new CompositePrizeApplier(bomb, mine));
    appliers.put(PrizeTypes.BOUNCINGBULLETS, new BouncingBulletsPrizeApplier());
    appliers.put(PrizeTypes.BRICK, new BrickPrizeApplier());
    appliers.put(PrizeTypes.BURST, burst);
    appliers.put(PrizeTypes.CLOAK, new CloakPrizeApplier());
    appliers.put(PrizeTypes.DECOY, new DecoyPrizeApplier());
    appliers.put(PrizeTypes.DUD, new DudPrizeApplier());
    appliers.put(PrizeTypes.ENERGY, new EnergyPrizeApplier());
    appliers.put(PrizeTypes.GLUE, new GluePrizeApplier());
    appliers.put(PrizeTypes.GUN, bullet);
    appliers.put(PrizeTypes.MULTIFIRE, new MultiFirePrizeApplier());
    appliers.put(PrizeTypes.MULTIPRIZE, new MultiPrizePrizeApplier());
    appliers.put(PrizeTypes.PORTAL, new PortalPrizeApplier());
    appliers.put(PrizeTypes.PROXIMITY, new ProximityPrizeApplier());
    appliers.put(PrizeTypes.QUICKCHARGE, new QuickChargePrizeApplier());
    appliers.put(PrizeTypes.RECHARGE, new RechargePrizeApplier());
    appliers.put(PrizeTypes.REPEL, new RepelPrizeApplier());
    appliers.put(PrizeTypes.ROCKET, new RocketPrizeApplier());
    appliers.put(PrizeTypes.ROTATION, new RotationPrizeApplier());
    appliers.put(PrizeTypes.SHIELDS, new ShieldsPrizeApplier());
    appliers.put(PrizeTypes.SHRAPNEL, new ShrapnelPrizeApplier());
    appliers.put(PrizeTypes.STEALTH, new StealthPrizeApplier());
    appliers.put(PrizeTypes.SUPER, new SuperPrizeApplier());
    appliers.put(PrizeTypes.THOR, new ThorPrizeApplier());
    appliers.put(PrizeTypes.THRUSTER, new ThrusterPrizeApplier());
    appliers.put(PrizeTypes.TOPSPEED, new TopSpeedPrizeApplier());
    appliers.put(PrizeTypes.WARP, new WarpPrizeApplier());
    appliers.put(PrizeTypes.XRADAR, new XRadarPrizeApplier());

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
   * by reading the typed {@code prizeWeights()} slot on its
   * {@link infinity.settings.ConfigRegistry}. Keys with weight {@code 0}
   * are dropped (matches the "weight 0 means never spawn" convention).
   *
   * @return the per-arena selector, or {@link #globalFallbackSelector} when
   *     the arena has no usable {@code prizeWeights} entries
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
    if (log.isInfoEnabled()) {
      log.info(
          "PrizeWeight selector built for arena '{}' with {} non-zero entries",
          arenaName,
          weights.size());
    }
    return selector;
  }

  private java.util.Map<String, Integer> readArenaWeights(final String arenaName) {
    // B3: typed pipeline. PrizeWeightsAdapter parses prize-weights.groovy
    // into the typed PrizeWeightsConfig slot on the arena's ConfigRegistry;
    // we filter out 0-weight entries here (matches the legacy "weight 0
    // means never spawn" convention).
    final infinity.es.arena.ArenaId arenaId =
        new infinity.es.arena.ArenaId(arenaName, com.simsilica.es.EntityId.NULL_ID);
    final java.util.Map<String, Integer> source =
        configRegistry.forArena(arenaId).prizeWeights().weights();
    final java.util.Map<String, Integer> weights = new HashMap<>();
    for (final var entry : source.entrySet()) {
      if (entry.getValue() > 0) {
        weights.put(entry.getKey(), entry.getValue());
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

    pruneRemovedPrizes();

    prizeSpawners.applyChanges();

    pruneRemovedSpawners();

    // Hoist ship-id collection above the spawner loop — every spawner in the
    // arena needs the same per-arena player count, so paying the EntitySet
    // iteration cost once per tick (not once per spawner) is a small win.
    final java.util.List<EntityId> shipIds = new java.util.ArrayList<>(ships.size());
    for (Entity ship : ships) {
      shipIds.add(ship.getId());
    }

    for (Entity entitySpawner : prizeSpawners) {
      EntityId spawnerId = entitySpawner.getId();
      Spawner s = entitySpawner.get(Spawner.class);
      SpawnPosition p = entitySpawner.get(SpawnPosition.class);
      SphereShape c = entitySpawner.get(SphereShape.class);

      // Slice 8d: per-arena player count drives additive count + radius scaling.
      // Spawners without an ArenaId (legacy BasicEnvironment) get playerCount=0
      // → scaling collapses, behaviour identical to pre-Slice-8d.
      final ArenaId spawnerArenaId = ed.getComponent(spawnerId, ArenaId.class);
      final int playersInArena = countPlayersInArena(ed, shipIds, spawnerArenaId);
      final int effectiveMaxCount =
          computeEffectiveMaxCount(s.getMaxCount(), s.getCountPerPlayer(), playersInArena);
      final double effectiveRadius =
          computeEffectiveRadius(c.getRadius(), s.getRadiusPerPlayer(), playersInArena);

      Set<EntityId> spawnerBountySet = spawnerBounties.get(spawnerId);
      if (spawnerBountySet == null) {
        // First time we see this spawner: prime with one prize.
        spawnerBountySet = new HashSet<>();
        EntityId idBounty = spawnBounty(spawnerId, s, p.getLocation(), effectiveRadius);
        spawnerBountySet.add(idBounty);
        spawnerBounties.put(spawnerId, spawnerBountySet);
        spawnerLastSpawned.put(spawnerId, 0d);

      } else if (spawnerBountySet.size() < effectiveMaxCount
          && spawnerLastSpawned.get(spawnerId) > s.getSpawnInterval()) {
        // Interval met and below the effective cap: regenerate up to a full
        // batch, but never overshoot the cap. Subspace [Prize] PrizeHideCount
        // analogue.
        final int batch =
            computeRegenAmount(s.getRegenBatch(), spawnerBountySet.size(), effectiveMaxCount);
        for (int i = 0; i < batch; i++) {
          EntityId idBounty = spawnBounty(spawnerId, s, p.getLocation(), effectiveRadius);
          spawnerBountySet.add(idBounty);
        }
        spawnerLastSpawned.put(spawnerId, 0d);
      }

      spawnerLastSpawned.put(spawnerId, spawnerLastSpawned.get(spawnerId) + 1000 * time.getTpf());
    }
  }

  /**
   * For each prize that left the {@code prizes} set this tick, drop its id
   * from every spawner's bounty set so the per-spawner count stays accurate.
   * Extracted from {@link #update} as a complexity ratchet.
   */
  private void pruneRemovedPrizes() {
    for (Entity bountyRemoved : prizes.getRemovedEntities()) {
      EntityId idBounty = bountyRemoved.getId();
      for (Entity entitySpawner : prizeSpawners) {
        Set<EntityId> spawnerBountySet = spawnerBounties.get(entitySpawner.getId());
        spawnerBountySet.remove(idBounty);
        spawnerBounties.put(entitySpawner.getId(), spawnerBountySet);
      }
    }
  }

  /**
   * Drop cached selectors / bounty sets / last-spawned timestamps for spawners
   * that left {@code prizeSpawners} this tick (arena unload, explicit despawn).
   * Extracted from {@link #update} as a complexity ratchet.
   */
  private void pruneRemovedSpawners() {
    for (Entity removedSpawner : prizeSpawners.getRemovedEntities()) {
      spawnerSelectors.remove(removedSpawner.getId());
      spawnerBounties.remove(removedSpawner.getId());
      spawnerLastSpawned.remove(removedSpawner.getId());
    }
  }

  /**
   * Slice 8d additive max-count formula. Effective cap =
   * {@code baseMax + countPerPlayer × playerCount}. {@code countPerPlayer ==
   * 0} or {@code playerCount == 0} collapses to the base. Static + visible
   * for testing — pure function, no PrizeSystem state required.
   */
  static int computeEffectiveMaxCount(
      final int baseMax, final int countPerPlayer, final int playerCount) {
    return baseMax + countPerPlayer * playerCount;
  }

  /**
   * Slice 8d additive radius formula. Effective radius =
   * {@code baseRadius + radiusPerPlayer × playerCount}. Static + visible for
   * testing.
   */
  static double computeEffectiveRadius(
      final double baseRadius, final double radiusPerPlayer, final int playerCount) {
    return baseRadius + radiusPerPlayer * playerCount;
  }

  /**
   * Slice 8d regen-batch budget. Spawn at most {@code regenBatch} per
   * interval, but never overshoot the effective cap (deficit-capped).
   * {@code regenBatch} below {@code 1} clamps to {@code 1} so a misconfigured
   * spawner still trickles rather than freezing entirely; deficit at or
   * below zero returns {@code 0}. Static + visible for testing.
   */
  static int computeRegenAmount(
      final int regenBatch, final int currentAlive, final int effectiveMaxCount) {
    final int deficit = effectiveMaxCount - currentAlive;
    if (deficit <= 0) {
      return 0;
    }
    return Math.min(Math.max(regenBatch, 1), deficit);
  }

  /**
   * Count active ships whose {@link ArenaId} matches {@code targetArena}.
   * Static + takes {@code Iterable<EntityId>} (rather than the live
   * {@code EntitySet}) so tests can build the collection without booting
   * the system manager.
   *
   * <p>Returns {@code 0} when {@code targetArena} is null (legacy spawners
   * with no arena tagging) so additive scaling collapses to base values —
   * preserves pre-Slice-8d behaviour for un-tagged spawners.
   */
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

  /**
   * Spawn one prize for {@code spawner}. Resolves the prize-decay from
   * (in priority order):
   *
   * <ol>
   *   <li>The spawner's per-spawner TTL ({@link Spawner#getSpawnedDecayMillis()})
   *       — set when the spawner was declared in {@code arena.groovy} with
   *       a {@code ttlMs} field.
   *   <li>The arena's typed {@link infinity.config.PrizeConfig#defaultDecayMs()}
   *       — read via the spawner's {@link ArenaId} from
   *       {@link infinity.settings.ConfigRegistrySystem}.
   *   <li>{@link infinity.config.PrizeConfig#DEFAULTS} (legacy
   *       {@code 20000} ms) when the spawner has no {@code ArenaId}.
   * </ol>
   *
   * <p>Always passes a non-zero {@code decayMillis} to
   * {@link GameEntities#createPrize} so the api-side fallback constant
   * {@code GameEntities.PRIZE_DEFAULT_DECAY_MS} is reserved for direct
   * module-author calls without server context.
   *
   * <p>Prize-type weighting goes through {@link #getPrizeType(EntityId)}
   * which honours {@link PrizeWeightsOverride} on the spawner, falling back
   * to the spawner's arena defaults, then the global {@link #FALLBACK_WEIGHTS}.
   */
  private EntityId spawnBounty(
      EntityId spawnerId, Spawner spawner, Vec3d spawnerLocation, double radius) {
    String prizeType = getPrizeType(spawnerId);
    final ArenaId arenaId = ed.getComponent(spawnerId, ArenaId.class);
    final infinity.config.PrizeConfig prize =
        arenaId == null
            ? infinity.config.PrizeConfig.DEFAULTS
            : configRegistry.forArena(arenaId).prize();
    prizeType = maybeRollNegative(prizeType, prize);
    Vec3d prizeSpawnLocation =
        this.getSpawnLocation(spawnerLocation, radius, spawner.spawnOnRing());
    final long decayMs = resolveDecayMs(spawnerId, spawner);
    return GameEntities.createPrize(
        ed,
        phys,
        ourTime.getTime(),
        prizeSpawnLocation,
        prizeType,
        decayMs,
        spawner.isHidden());
  }

  /**
   * Subspace 1-in-N negative-prize roll (REFERENCE.md {@code ## Prize}
   * → {@code PrizeNegativeFactor}). When the roll hits, the selected
   * prize-type is replaced by {@link PrizeTypes#DUD} (a no-op pickup
   * applier). Slice 8c implements the canonical odds via DUD
   * substitution rather than building inverse stat-degradation
   * appliers — the config knob preserves the probability semantics so
   * a follow-up slice can swap the substitution for proper inverse
   * appliers without re-authoring presets.
   *
   * <p>Short-circuits when:
   * <ul>
   *   <li>{@code prizeNegativeFactor <= 0} — roll disabled (default for
   *       un-authored arenas; preserves 1:1 behaviour).
   *   <li>The selected type is already {@link PrizeTypes#DUD} — re-rolling
   *       to DUD is a no-op.
   * </ul>
   */
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
      // Per-spawner ttlMs is a fixed override (precedence α from the
      // Slice 8a grill): explicit author intent wins, no randomisation.
      return spawner.getSpawnedDecayMillis();
    }
    final ArenaId arenaId = ed.getComponent(spawnerId, ArenaId.class);
    final infinity.config.PrizeConfig prize =
        arenaId == null
            ? infinity.config.PrizeConfig.DEFAULTS
            : configRegistry.forArena(arenaId).prize();
    return sampleDecayMs(prize);
  }

  /**
   * Uniform sample in {@code [defaultMinDecayMs, defaultDecayMs]} —
   * Subspace's {@code [Prize] PrizeMinExist..PrizeMaxExist} hidden-prize
   * lifetime range (REFERENCE.md {@code ## Prize}). When the arena hasn't
   * authored {@code minExist}, {@code defaultMinDecayMs == defaultDecayMs}
   * and the sample collapses to the upper bound — preserves 1:1 behaviour
   * for un-migrated arenas.
   */
  private long sampleDecayMs(final infinity.config.PrizeConfig prize) {
    final long min = prize.defaultMinDecayMs();
    final long max = prize.defaultDecayMs();
    if (min >= max) {
      return max;
    }
    // nextLong(origin, bound) is bound-exclusive; +1 makes the range inclusive.
    return random.nextLong(min, max + 1);
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
   * Spawn a single weighted prize at a ship's death point. Called from
   * {@code DeathSystem} when a {@link Player} ship transitions to
   * {@code Dead}.
   *
   * <p>Behaviour (Slice 8b, contract B from grilling — no threshold,
   * always-drop):
   *
   * <ul>
   *   <li>No-op when the ship's arena has {@code deathPrizeTimeMs == 0}
   *       (= death-drops disabled). Default for un-authored arenas.
   *   <li>Prize type selected from the ship's arena
   *       {@code [PrizeWeight]} table (same selector default-cadence
   *       spawners use for that arena). Falls back to the global
   *       fallback weights when the ship has no {@link ArenaId}.
   *   <li>Lifetime is {@code prize().deathPrizeTimeMs()} from the
   *       arena's {@link infinity.config.PrizeConfig} — distinct from
   *       the {@code [PrizeMinExist..PrizeMaxExist]} range used by
   *       default-cadence spawners.
   * </ul>
   *
   * <p>Threshold gating (e.g. "only drop if ship-bounty &gt; N") and
   * ship-bounty growth tracking are deferred to a later slice — that
   * mechanic doesn't exist in Infinity today. See
   * {@code .scratch/settings-pipeline-slices.md} sub-slice 8b.
   */
  public void spawnDeathPrize(
      final EntityId shipId, final Vec3d deathPosition, final long timeNs) {
    final ArenaId arenaId = ed.getComponent(shipId, ArenaId.class);
    final infinity.config.PrizeConfig prize =
        arenaId == null
            ? infinity.config.PrizeConfig.DEFAULTS
            : configRegistry.forArena(arenaId).prize();
    final long deathPrizeTimeMs = prize.deathPrizeTimeMs();
    if (deathPrizeTimeMs <= 0L) {
      return;
    }
    final String arenaName = arenaId == null ? null : arenaId.getArena();
    final String selected =
        arenaName == null
            ? globalFallbackSelector.next(random)
            : arenaSelector(arenaName).next(random);
    final String prizeType = maybeRollNegative(selected, prize);
    GameEntities.createPrize(ed, phys, timeNs, deathPosition, prizeType, deathPrizeTimeMs);
    log.info(
        "Death-drop: ship {} died in arena '{}' at {} → spawned {} (lifetime={} ms)",
        shipId,
        arenaName,
        deathPosition,
        prizeType,
        deathPrizeTimeMs);
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

  private void handlePrizeAcquisition(final PrizeType pt, final EntityId ship) {
    final String name = pt.getTypeName(ed);
    log.info("Ship {} picked up prize: {}", ship, name);
    final PrizeApplier applier = appliers.get(name);
    if (applier == null) {
      log.warn("Prize type {} has no registered applier; skipping", name);
      return;
    }
    try {
      applier.apply(ship, applierContext);
    } catch (final UnsupportedOperationException e) {
      // Stub applier — prize family is identified but the apply-time logic
      // hasn't landed yet. Degrade to a visible no-op rather than crashing
      // the contact loop. See the corresponding *PrizeApplier class for
      // family + intent.
      if (log.isWarnEnabled()) {
        log.warn("Prize type {} not yet implemented: {}", name, e.getMessage());
      }
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
