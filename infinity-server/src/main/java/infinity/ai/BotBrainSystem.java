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
import infinity.ai.field.ArenaNav;
import infinity.ai.field.ArenaSpatialFields;
import infinity.ai.field.NavigationFields;
import infinity.ai.objective.ArenaObjective;
import infinity.ai.objective.BotRoleRegistry;
import infinity.ai.objective.DeathmatchObjective;
import infinity.ai.steer.AvoidObstacles;
import infinity.ai.tactical.ArchetypeConfig;
import infinity.ai.tactical.Behaviour;
import infinity.ai.tactical.DisengageBehaviour;
import infinity.ai.tactical.AssassinateBehaviour;
import infinity.ai.tactical.EngageBehaviour;
import infinity.ai.tactical.FollowTrafficBehaviour;
import infinity.ai.tactical.HoldPositionBehaviour;
import infinity.ai.tactical.SearchBehaviour;
import infinity.ai.tactical.OwnBotState;
import infinity.ai.tactical.ServerBotAiArenaContext;
import infinity.ai.tactical.SituationalInputsFactory;
import infinity.ai.tactical.TacticalGoal;
import infinity.ai.tactical.TacticalPlanner;
import infinity.ai.tactical.TacticalPlannerImpl;
import infinity.config.BehaviourTweak;
import infinity.config.BotBrainConfig;
import infinity.config.BotsConfig;
import infinity.config.ShipConfig;
import infinity.config.ZoneBotAiConfig;
import infinity.es.BotDebug;
import infinity.es.BotRole;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.input.MovementInput;
import infinity.es.ship.BotShip;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.StealthActive;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.RadarRange;
import infinity.es.ship.ShipType;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineBotAiSystem;
import infinity.settings.ZoneBotAiConfigSystem;
import infinity.sim.WeaponsFiring;
import infinity.modules.ArenaModuleSystem;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.MapSystem;
import infinity.systems.ship.WeaponsFireEligibilitySystem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical writer of {@link MovementInput} on {@link BotShip} entities. Per-tick: sample
 * physics state, build perception, tick the per-bot brain BT (writes intent to its
 * blackboard), wrap with {@link AvoidObstacles} reactive steering, write MovementInput.
 * Per-bot wiring lives in {@link BrainContainer} (see {@code entity-containers.md}).
 * Default archetype is "Brawler" ({@link CombatantBrain}); slice #08 wires Groovy CCP to
 * pick alternative archetypes per ship. See ADR-0009.
 */
public final class BotBrainSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(BotBrainSystem.class);

  // Sentinel for "planner has never run for this bot" — the first tick plans unconditionally.
  private static final long UNPLANNED = Long.MIN_VALUE;

  // "none" — empty nav-diag / archetype-name sentinel.
  private static final String NONE = "none";

  // Shared 2-decimal format for the stuck/nav-field diagnostics (avoids duplicate-literal noise).
  private static final String FMT_2DP = "%.2f";

  // Stuck diagnostic: when a bot's speed drops below this (world units/sec), log the flow-field
  // heading vs its own facing — throttled per bot so it doesn't spam while wedged.
  private static final double STUCK_SPEED_SQ = 1.0;
  private static final long STUCK_LOG_THROTTLE_NANOS = 1_000_000_000L;

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
  private MapSystem mapSystem;
  private ArenaModuleSystem arenaModuleSystem;
  private TacticalPlanner planner;
  // Per-arena spatial-field production (nav builder, density/threat/opportunity/combat, chokepoint
  // pinning) lives here; the brain reads finished ArenaNavs. See ArenaSpatialFields.
  private ArenaSpatialFields spatialFields;
  // Names of the behaviours the planner can actually enumerate — others carry a derived weight
  // but can't yet become a goal (no Behaviour impl); the HUD marks the difference.
  private Set<String> selectableBehaviours = Set.of();
  private BrainContainer brains;
  // Dev-only flow-field debug overlay sampler (player ships); driven on the density cadence.
  private FlowFieldDebugSampler flowDebug;
  private final BrainRegistry brainRegistry = new BrainRegistry();
  // Shared reactive layer — stateless across bots, so one instance suffices.
  private final AvoidObstacles avoidObstacles =
      new AvoidObstacles(LOOK_AHEAD_DISTANCE, CORRIDOR_HALF_WIDTH, AVOID_THRUST);
  // Omnidirectional grid wall-repulsion (ADR-0011 #03): escapes corners/wall-grinding the
  // forward-only AvoidObstacles ray can't see. 2-cell reach (ships are radius-1) — narrowed from 3 so
  // it fires only when a wall is genuinely close, not on every bit of nearby structure (it hard-
  // overrides nav, so over-firing yanks bots off their flow heading). Full thrust for the escape.
  private final infinity.ai.steer.WallRepulsion wallRepulsion =
      new infinity.ai.steer.WallRepulsion(2, AVOID_THRUST);

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
    this.mapSystem = requireSystem(MapSystem.class);
    this.arenaModuleSystem = requireSystem(ArenaModuleSystem.class);
    final List<Behaviour> behaviours =
        List.of(
            new EngageBehaviour(this.engineBotAi::get),
            new AssassinateBehaviour(this.engineBotAi::get),
            new DisengageBehaviour(),
            new SearchBehaviour(),
            new FollowTrafficBehaviour(),
            new HoldPositionBehaviour());
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
    this.spatialFields =
        new ArenaSpatialFields(this.ed, this.space, this.mapSystem, this.zoneBotAi::get);
    this.spatialFields.start();
    this.brains =
        new BrainContainer(
            this.ed, this.brainRegistry, this.firing, this.configRegistrySystem);
    this.brains.start();
    this.flowDebug =
        new FlowFieldDebugSampler(
            this.ed,
            this.space,
            this.mapSystem,
            this.arenaModuleSystem,
            this.spatialFields,
            this.zoneBotAi::get);
    this.flowDebug.start();
  }

  @Override
  public void stop() {
    if (this.brains != null) {
      this.brains.stop();
      this.brains = null;
    }
    if (this.spatialFields != null) {
      this.spatialFields.stop();
      this.spatialFields = null;
    }
    if (this.flowDebug != null) {
      this.flowDebug.stop();
      this.flowDebug = null;
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
    this.spatialFields.refresh(nowNanos);
    this.flowDebug.refresh(nowNanos);
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
    final Frequency freq = this.ed.getComponent(wiring.botId, Frequency.class);
    bb.setOwnFreq(freq != null ? freq.getFrequency() : -1);
    final ZoneBotAiConfig zoneCfg = this.zoneBotAi.get();
    bb.setNavBlendWeights(zoneCfg.navThreatWeight(), zoneCfg.navOpportunityWeight());
    final Energy energy = this.ed.getComponent(wiring.botId, Energy.class);
    final EnergyStats energyStats = this.ed.getComponent(wiring.botId, EnergyStats.class);
    bb.setEnergy(
        energy != null ? energy.getEnergy() : -1,
        energyStats != null ? energyStats.max() : -1);

    // Refresh capability derivation when the arena config changed (incl. EMPTY→loaded once
    // ArenaId is stamped by membership), then re-select a goal on the planner cadence.
    refreshDerivation(wiring);
    bb.setArenaContext(wiring.arenaContext);
    ensureRoleBias(wiring, bb);
    planOnCadence(wiring, bb, nowNanos);

    bb.resetIntent();
    bb.setLastBranch("Idle");
    bb.setNavDiag(NONE); // overwritten by SteerApproachTarget when the approach branch runs
    // Cooperative wall-clearance: hand the brain a world-space push off nearby walls so SteerToGoalTile
    // blends it into the flow heading (the hull eases off walls while still following the route),
    // instead of a reverse-override fighting the flow. See resolveSteer + WallRepulsion#repulsion.
    final NavigationFields navFields =
        wiring.arenaContext == null ? null : wiring.arenaContext.navigation();
    bb.setWallAvoid(
        navFields == null
            ? null
            : this.wallRepulsion.repulsion(
                self, navFields, wiring.arenaContext.originCellX(), wiring.arenaContext.originCellZ()));
    wiring.brain.tick(bb);
    final Steer steer = resolveSteer(wiring, self, snapshot, bb);
    this.ed.setComponent(wiring.botId, new MovementInput(steer.move, new Quatd(), MovementInput.NONE));
    writeDebugSnapshot(wiring, self, target, steer.move, bb, steer.overlay);
    maybeLogStuck(wiring, self, target, steer.move, bb, nowNanos, steer.overlay);
  }

  /**
   * Resolve the final movement intent + its debug overlay. <b>The flow field is primary; reactive
   * avoidance cooperates rather than overrides</b> (ADR-0011). While steering down a flow gradient
   * (Navigate), there is <em>no</em> reactive override: the flow already routes around all static
   * structure (every non-zero {@code .lvl} tile is impassable in the grid), and wall hull-clearance is
   * blended into the heading upstream in {@code SteerToGoalTile} via {@link Blackboard#wallAvoid}. Off
   * the nav path (combat branches with no flow heading), the reactive layers still hard-override:
   * {@link infinity.ai.steer.WallRepulsion} (grind escape, full authority) &gt; forward-ray
   * {@link AvoidObstacles}. Oversteer-damping applies to all but the wall-escape.
   */
  private Steer resolveSteer(
      final BrainWiring wiring,
      final MoverState self,
      final PerceptionSnapshot snapshot,
      final Blackboard bb) {
    if (!"Navigate".equals(bb.lastBranch())) {
      final NavigationFields nav =
          wiring.arenaContext == null ? null : wiring.arenaContext.navigation();
      final Vec3d wallPush =
          nav == null
              ? null
              : this.wallRepulsion.steer(
                  self, nav, wiring.arenaContext.originCellX(), wiring.arenaContext.originCellZ());
      if (wallPush != null) {
        return new Steer(wallPush, "+WallRepel"); // no damping — keep full corner-escape authority
      }
      final Vec3d avoid = this.avoidObstacles.steer(self, snapshot);
      if (avoid != null) {
        final Vec3d move = avoid.clone();
        dampOversteer(move);
        return new Steer(move, "+Avoid");
      }
    }
    // Nav intent is already alignment-shaped by SeekDirection (thrust scales with heading alignment),
    // so a second oversteer-damp here would compound to ~zero thrust at sharp turns and stall the bot
    // mid-turn through a chokepoint. Use the shaped intent directly.
    return new Steer(bb.intent().clone(), "");
  }

  /** Final movement intent + the reactive-layer overlay tag for the debug HUD. */
  private static final class Steer {
    private final Vec3d move;
    private final String overlay;

    Steer(final Vec3d move, final String overlay) {
      this.move = move;
      this.overlay = overlay;
    }
  }

  /**
   * When a bot's speed is near zero (wedged), log the flow-field heading toward its target vs its
   * own facing — the fastest way to see "field says turn around, bot faces the wall." Throttled per
   * bot to {@link #STUCK_LOG_THROTTLE_NANOS}.
   */
  // Guarded by the isInfoEnabled early-return below; PMD doesn't recognise the guard form.
  @SuppressWarnings("PMD.GuardLogStatement")
  private void maybeLogStuck(
      final BrainWiring wiring,
      final MoverState self,
      @Nullable final NearbyShip target,
      final Vec3d move,
      final Blackboard bb,
      final long nowNanos,
      final String overlay) {
    if (!log.isInfoEnabled() || self.velocity().lengthSq() > STUCK_SPEED_SQ) {
      return;
    }
    if (wiring.lastStuckLogNanos != UNPLANNED
        && nowNanos - wiring.lastStuckLogNanos < STUCK_LOG_THROTTLE_NANOS) {
      return;
    }
    wiring.lastStuckLogNanos = nowNanos;
    final Vec3d fwd = self.orientation().mult(Vec3d.UNIT_Z);
    String flow = "n/a";
    final ServerBotAiArenaContext ctx = wiring.arenaContext;
    if (target != null && ctx != null && ctx.navigation() != null) {
      final int sx = (int) Math.floor(self.position().x) - ctx.originCellX();
      final int sy = (int) Math.floor(self.position().z) - ctx.originCellZ();
      final int gx = (int) Math.floor(target.position().x) - ctx.originCellX();
      final int gy = (int) Math.floor(target.position().z) - ctx.originCellZ();
      final infinity.math.Vec2d g = ctx.navigation().gradientFor(gx, gy).directionAt(sx, sy);
      flow = String.format("(%.2f,%.2f)", g.x, g.y);
    }
    log.info(
        "bot {} stuck v~0 @({},{}) facing=({},{}) flowToTarget={} move=(turn{},thr{}) branch={}{} nav={}",
        wiring.botId.getId(),
        (int) self.position().x,
        (int) self.position().z,
        String.format(FMT_2DP, fwd.x),
        String.format(FMT_2DP, fwd.z),
        flow,
        String.format("%+.2f", move.x),
        String.format("%+.2f", move.z),
        bb.lastBranch(),
        overlay,
        bb.navDiag());
    logNavField(bb, ctx, self);
  }

  /**
   * Diagnostic: when a stuck bot is on a {@link infinity.ai.tactical.NavigateToTile} goal, dump the
   * flow field around its cell — self/goal arena-relative cells, the gradient toward the goal, and a
   * 5x5 window of passability ({@code #}/{@code .}) + distances — straight from the live field, so the
   * routing can be read without reverse-engineering the world&rarr;grid transform offline.
   */
  @SuppressWarnings("PMD.GuardLogStatement")
  private void logNavField(
      final Blackboard bb, @Nullable final ServerBotAiArenaContext ctx, final MoverState self) {
    if (ctx == null || ctx.navigation() == null
        || !(bb.currentGoal() instanceof infinity.ai.tactical.NavigateToTile goal)) {
      return;
    }
    final NavigationFields nav = ctx.navigation();
    final int sx = (int) Math.floor(self.position().x) - ctx.originCellX();
    final int sy = (int) Math.floor(self.position().z) - ctx.originCellZ();
    final int gx = goal.cellX() - ctx.originCellX();
    final int gy = goal.cellZ() - ctx.originCellZ();
    final infinity.ai.field.DistanceField field = nav.fieldFor(gx, gy);
    final infinity.math.Vec2d g = nav.gradientFor(gx, gy).directionAt(sx, sy);
    log.info(
        "  nav-field: self-cell=({},{}) goal-cell=({},{}) gradTowardGoal=({},{}) selfDist={}",
        sx, sy, gx, gy,
        String.format(FMT_2DP, g.x), String.format(FMT_2DP, g.y),
        String.format("%.1f", field.valueAt(sx, sy)));
    for (int dz = -2; dz <= 2; dz++) {
      final StringBuilder row = new StringBuilder("  ");
      for (int dx = -2; dx <= 2; dx++) {
        final int cx = sx + dx;
        final int cz = sy + dz;
        if (dx == 0 && dz == 0) {
          row.append("   B   ");
        } else if (!nav.passableAt(cx, cz)) {
          row.append("   #   ");
        } else {
          final double d = field.valueAt(cx, cz);
          row.append(String.format("%6.1f ", d));
        }
      }
      log.info(row.toString());
    }
  }

  /**
   * Ensure the bot has a {@link BotRole} — assigned once from the arena objective at spawn (ADR-0015),
   * held for the round — and publish its behaviour bias to the blackboard for the planner. Canonical
   * writer of {@link BotRole}.
   */
  private void ensureRoleBias(final BrainWiring wiring, final Blackboard bb) {
    final BotRoleRegistry registry = this.engineBotAi.roles();
    BotRole role = this.ed.getComponent(wiring.botId, BotRole.class);
    if (role == null) {
      role = assignRole(wiring, registry);
      this.ed.setComponent(wiring.botId, role);
    }
    bb.setRoleBias(registry.get(role.name()).behaviourBias());
  }

  /** Objective-assigned role validated against the registry (unknown name ⇒ default). */
  private BotRole assignRole(final BrainWiring wiring, final BotRoleRegistry registry) {
    final ServerBotAiArenaContext ctx = wiring.arenaContext;
    if (ctx == null || ctx.objective() == null) {
      return new BotRole(BotRole.DEFAULT);
    }
    final String name = ctx.objective().assignRole(wiring.botId, this::teamFreq);
    return registry.isRegistered(name) ? new BotRole(name) : new BotRole(BotRole.DEFAULT);
  }

  /** {@link infinity.ai.objective.ArenaSnapshot} accessor: the bot's frequency (team), or {@code -1}. */
  private int teamFreq(final EntityId bot) {
    final Frequency freq = this.ed.getComponent(bot, Frequency.class);
    return freq != null ? freq.getFrequency() : -1;
  }

  /** Re-select the bot's {@link TacticalGoal} once the planner cadence has elapsed (ADR-0013). */
  private void planOnCadence(final BrainWiring wiring, final Blackboard bb, final long nowNanos) {
    final ZoneBotAiConfig zoneCfg = this.zoneBotAi.get();
    final long cadenceNanos = zoneCfg.plannerCadenceMillis() * 1_000_000L;
    if (!shouldPlan(wiring.lastPlanNanos, nowNanos, cadenceNanos)) {
      return;
    }
    wiring.lastPlanNanos = nowNanos;
    // ADR-0016: snapshot the situational-input vocabulary once per planner cycle; behaviours read it.
    bb.setSituationalInputs(
        SituationalInputsFactory.compute(bb, ownState(wiring.botId), zoneCfg));
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

  /** Sample the bot's own ECS state the input vocabulary needs (weapon cooldown + concealment). */
  private OwnBotState ownState(final EntityId bot) {
    final BulletFireDelay delay = this.ed.getComponent(bot, BulletFireDelay.class);
    final boolean weaponReady = delay == null || delay.getPercent() >= 1.0;
    final CloakActive cloak = this.ed.getComponent(bot, CloakActive.class);
    final StealthActive stealth = this.ed.getComponent(bot, StealthActive.class);
    final boolean concealed =
        (cloak != null && cloak.isActive()) || (stealth != null && stealth.isActive());
    return new OwnBotState(weaponReady, concealed);
  }

  /**
   * Re-derive the bot's {@link CapabilityProfile} + {@link ArchetypeConfig} + arena context when its
   * arena's config snapshot changes (cheap reference compare). Stamps the server-only
   * {@link BotCapability} cache and rebuilds the weight vector the planner scores against.
   */
  // Intentional identity compares: ConfigRegistrySystem.replace() / MapSystem swap the snapshot +
  // passability references atomically, so reference inequality is exactly the "changed" signal
  // (cheaper than, and not equivalent to, a deep equals).
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  private void refreshDerivation(final BrainWiring wiring) {
    final ArenaId arenaId = this.ed.getComponent(wiring.botId, ArenaId.class);
    final ConfigRegistry registry =
        arenaId == null ? ConfigRegistry.EMPTY : this.configRegistrySystem.forArena(arenaId);
    final boolean[][] passable =
        arenaId == null ? null : this.mapSystem.passability(arenaId.getArena());
    final boolean registryChanged = registry != wiring.derivedFrom;
    final boolean passabilityChanged = passable != wiring.passabilityRef;
    if (!registryChanged && !passabilityChanged) {
      return; // nothing this bot derives from has changed
    }
    wiring.derivedFrom = registry;
    wiring.passabilityRef = passable;

    final BotSynergyTable synergy = this.engineBotAi.get();
    final ArenaCapabilityNorms norms = CapabilityDeriver.deriveNorms(shipConfigs(registry));
    wiring.arenaContext = buildArenaContext(arenaId, passable, norms, synergy);

    if (registryChanged) {
      final CapabilityProfile profile = deriveProfile(wiring.botId, registry, norms);
      this.ed.setComponent(wiring.botId, new BotCapability(profile));
      wiring.archetype =
          toArchetype(profile, synergy, this.zoneBotAi.get().minBehaviourWeight(), registry.bots());
    }
  }

  /** Bundle the per-arena bot-AI context: norms + synergy + (once the map loads) flow-field nav, density + origin. */
  // PMD UnusedPrivateMethod false-positives on the boolean[][] param; called from refreshDerivation.
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ServerBotAiArenaContext buildArenaContext(
      @Nullable final ArenaId arenaId,
      @Nullable final boolean[][] passable,
      final ArenaCapabilityNorms norms,
      final BotSynergyTable synergy) {
    if (arenaId == null || passable == null) {
      return new ServerBotAiArenaContext(
          norms, synergy, null, 0, 0, null, null, null, null, List.of(), 0.0,
          new DeathmatchObjective());
    }
    final ArenaObjective objective = this.arenaModuleSystem.objectiveFor(arenaId);
    final ArenaNav nav =
        this.spatialFields.forArena(arenaId.getArena(), passable, objective.staticGoalTiles());
    return new ServerBotAiArenaContext(
        norms,
        synergy,
        nav.fields(),
        nav.originX(),
        nav.originZ(),
        nav.density(),
        nav.threat(),
        nav.opportunity(),
        nav.combat(),
        nav.chokepointPool(),
        this.zoneBotAi.get().chokepointDensityWeight(),
        objective);
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
      return new ArchetypeConfig(NONE, Map.of());
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
   * Write the per-tick {@link BotDebug} snapshot. {@code overlay} suffixes the branch with the
   * reactive layer that overrode the BT this tick ({@code +WallRepel} / {@code +Avoid}, or empty).
   * Clock hour is {@code 0} when there's no target; otherwise {@code 1..12} relative to the bot's
   * forward. The v2 fields (goal / weights / nav mode) surface the tactical layer for the debug
   * HUD (#08); objective + role stay empty until #06.
   */
  private void writeDebugSnapshot(
      final BrainWiring wiring,
      final MoverState self,
      final NearbyShip target,
      final Vec3d move,
      final Blackboard bb,
      final String overlay) {
    final String effectiveBranch = bb.lastBranch() + overlay;
    final long targetId = target != null ? target.id().getId() : -1L;
    final int clockHour = target != null ? clockHourToTarget(self, target.position()) : 0;
    final TacticalGoal goal = bb.currentGoal();
    // Nav diagnostic: "off" = no flow-field nav wired for this arena (passability missing); "flow" =
    // steering off a field this tick (NavigateToTile goal or the navigate-to-threat Approach branch);
    // "reactive" = nav available but using BT/steering this tick.
    final boolean navAvailable = bb.arenaContext() != null && bb.arenaContext().navigation() != null;
    final boolean flow =
        navAvailable
            && (goal instanceof infinity.ai.tactical.NavigateToTile
                || "Approach".equals(bb.lastBranch()));
    // reactive:<reason> exposes WHY the flow-field approach didn't steer (building/unreach/at-goal/…)
    // so a persistent reactive state is diagnosable rather than opaque. See SteerApproachTarget.
    final String navMode = !navAvailable ? "off" : flow ? "flow" : "reactive:" + bb.navDiag();
    final ShipType shipType = this.ed.getComponent(wiring.botId, ShipType.class);
    final String shipName = shipType != null && shipType.getType() != null
        ? shipType.getType().name()
        : "";
    // objective() is contractually non-null (DeathmatchObjective default) once a context exists.
    final String objectiveName =
        bb.arenaContext() != null ? bb.arenaContext().objective().name() : "";
    final BotRole role = this.ed.getComponent(wiring.botId, BotRole.class);
    final String roleName = role != null ? role.name() : "";
    this.ed.setComponent(
        wiring.botId,
        new BotDebug(
            effectiveBranch,
            targetId,
            move.x,
            move.z,
            clockHour,
            shipName,
            objectiveName,
            roleName,
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
      return "NavTile(" + n.cellX() + "," + n.cellZ() + ")";
    }
    if (goal instanceof infinity.ai.tactical.Search) {
      return "Search";
    }
    return NONE;
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
    long lastStuckLogNanos = UNPLANNED;
    ArchetypeConfig archetype = new ArchetypeConfig(NONE, Map.of());
    @Nullable ConfigRegistry derivedFrom;
    @Nullable boolean[][] passabilityRef;
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
