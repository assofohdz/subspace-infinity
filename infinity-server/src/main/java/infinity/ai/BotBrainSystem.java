// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.SimTime;
import infinity.ai.brain.Blackboard;
import infinity.ai.brain.BrainArchetype;
import infinity.ai.brain.BrainRegistry;
import infinity.ai.brain.CombatantBrain;
import infinity.ai.bt.Behavior;
import infinity.ai.capability.ArenaCapabilityNorms;
import infinity.ai.capability.BotCapability;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.capability.CapabilityDeriver;
import infinity.ai.capability.CapabilityProfile;
import infinity.ai.steer.AvoidObstacles;
import infinity.ai.tactical.ArchetypeConfig;
import infinity.ai.tactical.Behaviour;
import infinity.ai.tactical.DisengageBehaviour;
import infinity.ai.tactical.EngageBehaviour;
import infinity.ai.tactical.SearchBehaviour;
import infinity.ai.tactical.ServerBotAiArenaContext;
import infinity.ai.tactical.TacticalGoal;
import infinity.ai.tactical.TacticalPlanner;
import infinity.ai.tactical.TacticalPlannerImpl;
import infinity.config.BehaviourTweak;
import infinity.config.BotBrainConfig;
import infinity.config.BotsConfig;
import infinity.config.ShipConfig;
import infinity.config.ZoneBotAiConfig;
import infinity.es.BotDebug;
import infinity.es.arena.ArenaId;
import infinity.es.input.MovementInput;
import infinity.es.ship.BotShip;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.RadarRange;
import infinity.es.ship.ShipType;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineBotAiSystem;
import infinity.settings.ZoneBotAiConfigSystem;
import infinity.sim.WeaponsFiring;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.ship.WeaponsFireEligibilitySystem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Canonical writer of {@link MovementInput} on {@link BotShip} entities. Per-tick: sample
 * physics state, build perception, tick the per-bot brain BT (writes intent to its
 * blackboard), wrap with {@link AvoidObstacles} reactive steering, write MovementInput.
 * Per-bot wiring lives in {@link BrainContainer} (see {@code entity-containers.md}).
 * Default archetype is "Brawler" ({@link CombatantBrain}); slice #08 wires Groovy CCP to
 * pick alternative archetypes per ship. See ADR-0009.
 */
public final class BotBrainSystem extends BaseInfinitySystem {

  // Sentinel for "planner has never run for this bot" — the first tick plans unconditionally.
  private static final long UNPLANNED = Long.MIN_VALUE;

  // Default perception radius (world units) when neither RadarRange nor BotBrainConfig
  // is available — preserved for safety; live arenas should always have BotBrainConfig.
  private static final double DEFAULT_PERCEPTION_RADIUS = 30.0;

  // AvoidObstacles look-ahead corridor (world units). Roughly 5 cells of lookahead at
  // GRID_CELL_SIZE = 1; tune per arena via BotBrainConfig once slice #08 lands.
  private static final double LOOK_AHEAD_DISTANCE = 5.0;

  // Half-width of the avoidance corridor (world units). Wider = avoid earlier;
  // narrower = squeeze through gaps. 0.6 ~= ship-and-a-half.
  private static final double CORRIDOR_HALF_WIDTH = 0.6;

  // AvoidObstacles thrust magnitude when the reactive steer overrides the BT decision.
  private static final double AVOID_THRUST = 1.0;

  // Oversteer damping floor — minimum thrust multiplier even at max turn rate. Empirical:
  // 0.3 keeps the bot moving (so it doesn't stall mid-turn) while reducing momentum
  // overshoot enough that sharp turns actually clear. See "Bots oversteer at high thrust"
  // entry in .scratch/code-todos-backlog.md.
  private static final double OVERSTEER_THRUST_FLOOR = 0.3;

  private EntityData ed;
  private Perception perception;
  private PhysicsSpace<EntityId, MBlockShape> space;
  private WeaponsFiring firing;
  private ConfigRegistrySystem configRegistrySystem;
  private EngineBotAiSystem engineBotAi;
  private ZoneBotAiConfigSystem zoneBotAi;
  private TacticalPlanner planner;
  // Names of the behaviours the planner can actually enumerate — others carry a derived weight
  // but can't yet become a goal (no Behaviour impl); the HUD marks the difference.
  private Set<String> selectableBehaviours = Set.of();
  private BrainContainer brains;
  private final BrainRegistry brainRegistry = new BrainRegistry();
  // Shared reactive layer — stateless across bots, so one instance suffices.
  private final AvoidObstacles avoidObstacles =
      new AvoidObstacles(LOOK_AHEAD_DISTANCE, CORRIDOR_HALF_WIDTH, AVOID_THRUST);

  @Override
  protected void initialize() {
    this.ed = requireSystem(EntityData.class);
    this.perception = requireSystem(Perception.class);
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> physics = requireSystem(MPhysSystem.class);
    this.space = physics.getPhysicsSpace();
    this.firing = requireSystem(WeaponsFireEligibilitySystem.class);
    this.configRegistrySystem = requireSystem(ConfigRegistrySystem.class);
    this.engineBotAi = requireSystem(EngineBotAiSystem.class);
    this.zoneBotAi = requireSystem(ZoneBotAiConfigSystem.class);
    final List<Behaviour> behaviours =
        List.of(new EngageBehaviour(), new DisengageBehaviour(), new SearchBehaviour());
    this.planner = new TacticalPlannerImpl(behaviours, this.zoneBotAi::get);
    this.selectableBehaviours =
        behaviours.stream().map(Behaviour::name).collect(Collectors.toUnmodifiableSet());
    this.brainRegistry.register(new CombatantBrain());
  }

  @Override
  protected void terminate() {
    // brains container started/stopped in start()/stop()
  }

  @Override
  public void start() {
    this.brains =
        new BrainContainer(
            this.ed, this.brainRegistry, this.firing, this.configRegistrySystem);
    this.brains.start();
  }

  @Override
  public void stop() {
    if (this.brains != null) {
      this.brains.stop();
      this.brains = null;
    }
  }

  @Override
  public void update(final SimTime time) {
    if (this.brains == null) {
      return;
    }
    this.brains.update();
    final long nowNanos = time.getTime();
    for (final BrainWiring wiring : this.brains.getArray()) {
      tickBot(wiring, nowNanos);
    }
  }

  /** Compute and write this tick's {@link MovementInput} for one bot. */
  private void tickBot(final BrainWiring wiring, final long nowNanos) {
    final MoverState self = sampleState(wiring.botId);
    if (self == null) {
      // Body not yet attached to the physics space; skip until next tick.
      return;
    }
    final double radius = perceptionRadius(wiring.botId);
    final PerceptionSnapshot snapshot = this.perception.perceive(wiring.botId, self, radius);
    final Blackboard bb = wiring.blackboard;
    bb.setSelf(self);
    bb.setPerception(snapshot);
    final NearbyShip target = pickNearestThreat(self, snapshot);
    bb.setTarget(target);
    final Energy energy = this.ed.getComponent(wiring.botId, Energy.class);
    final EnergyStats energyStats = this.ed.getComponent(wiring.botId, EnergyStats.class);
    bb.setEnergy(
        energy != null ? energy.getEnergy() : -1,
        energyStats != null ? energyStats.max() : -1);

    // Refresh capability derivation when the arena config changed (incl. EMPTY→loaded once
    // ArenaId is stamped by membership), then re-select a goal on the planner cadence.
    refreshDerivation(wiring);
    bb.setArenaContext(wiring.arenaContext);
    planOnCadence(wiring, bb, nowNanos);

    bb.resetIntent();
    bb.setLastBranch("Idle");
    wiring.brain.tick(bb);
    final Vec3d avoid = this.avoidObstacles.steer(self, snapshot);
    final boolean avoiding = avoid != null;
    final Vec3d move = avoiding ? avoid : bb.intent().clone();
    dampOversteer(move);
    this.ed.setComponent(wiring.botId, new MovementInput(move, new Quatd(), MovementInput.NONE));
    writeDebugSnapshot(wiring, self, target, move, bb, avoiding);
  }

  /** Re-select the bot's {@link TacticalGoal} once the planner cadence has elapsed (ADR-0013). */
  private void planOnCadence(final BrainWiring wiring, final Blackboard bb, final long nowNanos) {
    final ZoneBotAiConfig zoneCfg = this.zoneBotAi.get();
    final long cadenceNanos = zoneCfg.plannerCadenceMillis() * 1_000_000L;
    if (!shouldPlan(wiring.lastPlanNanos, nowNanos, cadenceNanos)) {
      return;
    }
    wiring.lastPlanNanos = nowNanos;
    final TacticalGoal goal = this.planner.select(bb, wiring.archetype);
    if (goal != null) {
      bb.setCurrentGoal(goal); // null ⇒ nothing offered; keep the running goal
    }
  }

  /**
   * Whether the planner should re-select this tick: the first run ({@link #UNPLANNED}) always
   * plans; otherwise the cadence must have elapsed. The {@code == UNPLANNED} short-circuit runs
   * before the subtraction so {@code nowNanos - Long.MIN_VALUE} can't overflow into a spurious
   * "not yet" — the bug that left bots permanently goal-less on the v1 fallback branch.
   */
  static boolean shouldPlan(final long lastPlanNanos, final long nowNanos, final long cadenceNanos) {
    return lastPlanNanos == UNPLANNED || nowNanos - lastPlanNanos >= cadenceNanos;
  }

  /**
   * Re-derive the bot's {@link CapabilityProfile} + {@link ArchetypeConfig} + arena context when its
   * arena's config snapshot changes (cheap reference compare). Stamps the server-only
   * {@link BotCapability} cache and rebuilds the weight vector the planner scores against.
   */
  // Intentional identity compare: ConfigRegistrySystem.replace() atomically swaps the snapshot
  // reference on reload, so reference inequality is exactly the "config changed" signal (cheaper
  // than, and not equivalent to, a deep ConfigRegistry.equals).
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  private void refreshDerivation(final BrainWiring wiring) {
    final ArenaId arenaId = this.ed.getComponent(wiring.botId, ArenaId.class);
    final ConfigRegistry registry =
        arenaId == null ? ConfigRegistry.EMPTY : this.configRegistrySystem.forArena(arenaId);
    if (registry == wiring.derivedFrom) {
      return; // snapshot unchanged — derived state still valid
    }
    wiring.derivedFrom = registry;

    final BotSynergyTable synergy = this.engineBotAi.get();
    final ArenaCapabilityNorms norms = CapabilityDeriver.deriveNorms(shipConfigs(registry));
    wiring.arenaContext = new ServerBotAiArenaContext(norms, synergy);

    final CapabilityProfile profile = deriveProfile(wiring.botId, registry, norms);
    this.ed.setComponent(wiring.botId, new BotCapability(profile));
    wiring.archetype =
        toArchetype(profile, synergy, this.zoneBotAi.get().minBehaviourWeight(), registry.bots());
  }

  /** Profile for the bot's ship type against {@code norms}, or {@code null} when the type isn't configured. */
  @Nullable
  private CapabilityProfile deriveProfile(
      final EntityId botId, final ConfigRegistry registry, final ArenaCapabilityNorms norms) {
    final ShipType shipType = this.ed.getComponent(botId, ShipType.class);
    if (shipType == null || shipType.getType() == null) {
      return null;
    }
    final ShipConfig shipConfig = registry.getShip(shipType.getType());
    return shipConfig == null ? null : CapabilityDeriver.derive(shipConfig, norms);
  }

  /**
   * Cross {@code profile} through the synergy table into raw weights, then apply the arena's
   * per-ship {@code bots { tweak: [...] }} overlay (ADR-0014) → effective {@link ArchetypeConfig}.
   */
  private static ArchetypeConfig toArchetype(
      @Nullable final CapabilityProfile profile,
      final BotSynergyTable synergy,
      final double minBehaviourWeight,
      final BotsConfig bots) {
    if (profile == null) {
      return new ArchetypeConfig("none", Map.of());
    }
    final Map<String, Double> raw = synergy.weightsFor(profile, minBehaviourWeight);
    final Map<String, Double> effective = applyTweaks(raw, bots.tweakFor(profile.ship()));
    return new ArchetypeConfig(profile.ship().name(), effective);
  }

  /**
   * Overlay {@code tweaks} onto derived weights (ADR-0014): a tweak's op applies to the behaviour's
   * derived weight ({@code 0} if not derived, so additive tweaks can re-introduce a behaviour);
   * a result {@code <= 0} drops the behaviour.
   */
  private static Map<String, Double> applyTweaks(
      final Map<String, Double> raw, final List<BehaviourTweak> tweaks) {
    if (tweaks.isEmpty()) {
      return raw;
    }
    final Map<String, Double> out = new java.util.LinkedHashMap<>(raw);
    for (final BehaviourTweak t : tweaks) {
      final double w = t.apply(out.getOrDefault(t.behaviour(), 0.0));
      if (w > 0.0) {
        out.put(t.behaviour(), w);
      } else {
        out.remove(t.behaviour());
      }
    }
    return out;
  }

  /** The arena's configured {@link ShipConfig} templates (for capability normalization). */
  private static List<ShipConfig> shipConfigs(final ConfigRegistry registry) {
    return registry.configuredShips().stream().map(registry::getShip).filter(c -> c != null).toList();
  }

  /**
   * Couples thrust magnitude to turn magnitude — sharp turns ease off thrust so
   * momentum doesn't overshoot the new heading. Applied to whatever intent reached us
   * (BT result or AvoidObstacles override). Factor floors at
   * {@link #OVERSTEER_THRUST_FLOOR} so the bot doesn't stall mid-turn.
   */
  private static void dampOversteer(final Vec3d move) {
    final double factor = Math.max(OVERSTEER_THRUST_FLOOR, 1.0 - Math.abs(move.x));
    move.z *= factor;
  }

  /**
   * Write the per-tick {@link BotDebug} snapshot. {@code branch} is suffixed with "+Avoid"
   * when {@link AvoidObstacles} overrode the BT decision. Clock hour is {@code 0} when
   * there's no target; otherwise {@code 1..12} relative to the bot's forward. The v2 fields
   * (goal / weights / nav mode) surface the tactical layer for the debug HUD (#08); objective
   * + role stay empty until #06.
   */
  private void writeDebugSnapshot(
      final BrainWiring wiring,
      final MoverState self,
      final NearbyShip target,
      final Vec3d move,
      final Blackboard bb,
      final boolean avoiding) {
    final String effectiveBranch = avoiding ? bb.lastBranch() + "+Avoid" : bb.lastBranch();
    final long targetId = target != null ? target.id().getId() : -1L;
    final int clockHour = target != null ? clockHourToTarget(self, target.position()) : 0;
    final TacticalGoal goal = bb.currentGoal();
    final String navMode = goal instanceof infinity.ai.tactical.NavigateToTile ? "flow" : "reactive";
    final ShipType shipType = this.ed.getComponent(wiring.botId, ShipType.class);
    final String shipName = shipType != null && shipType.getType() != null
        ? shipType.getType().name()
        : "";
    this.ed.setComponent(
        wiring.botId,
        new BotDebug(
            effectiveBranch,
            targetId,
            move.x,
            move.z,
            clockHour,
            shipName,
            "", // objectiveName — #06
            "", // roleName — #06
            formatGoal(goal),
            formatTopScores(wiring.archetype, this.selectableBehaviours),
            formatBreakdown(wiring.archetype),
            navMode));
  }

  /** Compact goal label, e.g. {@code Engage(42)} / {@code Search} / {@code none}. */
  private static String formatGoal(@Nullable final TacticalGoal goal) {
    if (goal instanceof infinity.ai.tactical.Engage e) {
      return "Engage(" + e.target().getId() + ")";
    }
    if (goal instanceof infinity.ai.tactical.Disengage d) {
      return "Disengage(" + d.threat().getId() + ")";
    }
    if (goal instanceof infinity.ai.tactical.NavigateToTile n) {
      return "NavTile(" + n.tile().getId() + ")";
    }
    if (goal instanceof infinity.ai.tactical.Search) {
      return "Search";
    }
    return "none";
  }

  /**
   * Top-3 {@code behaviour=weight} pairs from the derived archetype, descending; {@code -} if empty.
   * Selectable behaviours (those with a registered {@code Behaviour} impl) are prefixed {@code *} so
   * a high but unimplemented weight (e.g. {@code hold-position}) reads as inert, not a bug.
   */
  private static String formatTopScores(
      final ArchetypeConfig archetype, final Set<String> selectable) {
    return archetype.behaviourWeights().entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(3)
        .map(
            e ->
                String.format(
                    "%s%s=%.2f",
                    selectable.contains(e.getKey()) ? "*" : "", e.getKey(), e.getValue()))
        .reduce((a, b) -> a + " " + b)
        .orElse("-");
  }

  /** Factor breakdown for the top behaviour — capability only today (× obj × role with #06). */
  private static String formatBreakdown(final ArchetypeConfig archetype) {
    return archetype.behaviourWeights().entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(e -> String.format("%s cap=%.2f", e.getKey(), e.getValue()))
        .orElse("-");
  }

  /**
   * Map the direction from {@code self} to {@code targetWorldPos} into a 1..12 clock hour
   * relative to the bot's forward heading. 12 = ahead, 3 = 90° right, 6 = behind, 9 = 90° left.
   */
  private static int clockHourToTarget(final MoverState self, final Vec3d targetWorldPos) {
    final Vec3d forward = self.orientation().mult(Vec3d.UNIT_Z);
    final Vec3d left = self.orientation().mult(Vec3d.UNIT_X);
    final Vec3d delta = targetWorldPos.subtract(self.position());
    if (delta.lengthSq() < 1e-9) {
      return 12;
    }
    final Vec3d dir = delta.normalize();
    // Positive angle = target to bot's left; convert to clockwise degrees from "ahead".
    final double angleRad = Math.atan2(left.dot(dir), forward.dot(dir));
    final double degCw = ((-Math.toDegrees(angleRad) % 360.0) + 360.0) % 360.0;
    final int hour = ((int) Math.round(degCw / 30.0)) % 12;
    return hour == 0 ? 12 : hour;
  }

  /** Sample the bot's current position / orientation / velocity from its RigidBody. */
  private MoverState sampleState(final EntityId botId) {
    final RigidBody<EntityId, MBlockShape> body = this.space.getBinIndex().getRigidBody(botId);
    if (body == null) {
      return null;
    }
    return new MoverState(
        body.position.clone(), body.orientation.clone(), body.getLinearVelocity().clone());
  }

  /**
   * Resolve the bot's perception radius. Per-ship {@code RadarRange} (if present and
   * positive) wins; otherwise fall back to the arena's {@link BotBrainConfig#perceptionRadius()};
   * lastly to {@link #DEFAULT_PERCEPTION_RADIUS} when no config is loaded.
   */
  private double perceptionRadius(final EntityId botId) {
    final RadarRange rr = this.ed.getComponent(botId, RadarRange.class);
    if (rr != null && rr.getRange() > 0.0) {
      return rr.getRange();
    }
    final ArenaId arenaId = this.ed.getComponent(botId, ArenaId.class);
    if (arenaId != null) {
      final ConfigRegistry registry = this.configRegistrySystem.forArena(arenaId);
      if (registry != null) {
        return registry.botBrain().perceptionRadius();
      }
    }
    return DEFAULT_PERCEPTION_RADIUS;
  }

  /** Pick the nearest threat from the snapshot. Returns null if the threat list is empty. */
  private static NearbyShip pickNearestThreat(
      final MoverState self, final PerceptionSnapshot snapshot) {
    NearbyShip nearest = null;
    double minDistSq = Double.POSITIVE_INFINITY;
    for (final NearbyShip threat : snapshot.threats()) {
      final double dSq = threat.position().distanceSq(self.position());
      if (dSq < minDistSq) {
        minDistSq = dSq;
        nearest = threat;
      }
    }
    return nearest;
  }

  /** Per-bot brain instance + blackboard + tactical-planner state, from the registry archetype. */
  private static final class BrainWiring {
    final EntityId botId;
    final Behavior brain;
    final Blackboard blackboard;

    // Tactical-planner state (ADR-0013). Re-derived when derivedFrom changes; goal re-selected
    // when lastPlanNanos is older than the zone cadence.
    long lastPlanNanos = UNPLANNED;
    ArchetypeConfig archetype = new ArchetypeConfig("none", Map.of());
    @Nullable ConfigRegistry derivedFrom;
    @Nullable ServerBotAiArenaContext arenaContext;

    BrainWiring(
        final EntityId botId,
        final BrainArchetype brainArchetype,
        final BotBrainConfig config,
        final WeaponsFiring firing) {
      this.botId = botId;
      this.brain = brainArchetype.createRoot(config);
      this.blackboard = brainArchetype.createBlackboard(config);
      this.blackboard.setSelfId(botId);
      this.blackboard.setFiring(firing);
    }
  }

  /** Per-{@link BotShip} sidecar holding the brain wiring; see entity-containers.md. */
  private final class BrainContainer extends EntityContainer<BrainWiring> {

    private final BrainRegistry registry;
    private final WeaponsFiring firing;
    private final ConfigRegistrySystem configs;

    BrainContainer(
        final EntityData ed,
        final BrainRegistry registry,
        final WeaponsFiring firing,
        final ConfigRegistrySystem configs) {
      super(ed, BotShip.class);
      this.registry = registry;
      this.firing = firing;
      this.configs = configs;
    }

    @Override
    public BrainWiring[] getArray() {
      return super.getArray();
    }

    @Override
    protected BrainWiring addObject(final Entity e) {
      final BotBrainConfig config = resolveConfig(e.getId());
      final BrainArchetype archetype = this.registry.get(config.archetypeName());
      return new BrainWiring(e.getId(), archetype, config, this.firing);
    }

    /**
     * Look up the bot's arena, fetch its {@code BotBrainConfig}; fall back to
     * {@link BotBrainConfig#DEFAULTS} when the arena hasn't loaded a {@code bot-tuning.groovy}.
     */
    private BotBrainConfig resolveConfig(final EntityId botId) {
      final ArenaId arenaId = ed.getComponent(botId, ArenaId.class);
      if (arenaId == null) {
        return BotBrainConfig.DEFAULTS;
      }
      final ConfigRegistry registry = this.configs.forArena(arenaId);
      return registry != null ? registry.botBrain() : BotBrainConfig.DEFAULTS;
    }

    @Override
    protected void updateObject(final BrainWiring wiring, final Entity e) {
      // BotShip is a marker; ADR-0010 implementation watches BotBrainConfig and re-wires.
    }

    @Override
    protected void removeObject(final BrainWiring wiring, final Entity e) {
      // BrainWiring holds no resources to release; let GC clean up.
    }
  }
}
