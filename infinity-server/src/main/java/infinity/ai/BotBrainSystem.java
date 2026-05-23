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
import infinity.ai.steer.AvoidObstacles;
import infinity.config.BotBrainConfig;
import infinity.es.BotDebug;
import infinity.es.arena.ArenaId;
import infinity.es.input.MovementInput;
import infinity.es.ship.BotShip;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.RadarRange;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.sim.WeaponsFiring;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.ship.WeaponsFireEligibilitySystem;

/**
 * Canonical writer of {@link MovementInput} on {@link BotShip} entities. Per-tick: sample
 * physics state, build perception, tick the per-bot brain BT (writes intent to its
 * blackboard), wrap with {@link AvoidObstacles} reactive steering, write MovementInput.
 * Per-bot wiring lives in {@link BrainContainer} (see {@code entity-containers.md}).
 * Default archetype is "Brawler" ({@link CombatantBrain}); slice #08 wires Groovy CCP to
 * pick alternative archetypes per ship. See ADR-0009.
 */
public final class BotBrainSystem extends BaseInfinitySystem {

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
    for (final BrainWiring wiring : this.brains.getArray()) {
      tickBot(wiring);
    }
  }

  /** Compute and write this tick's {@link MovementInput} for one bot. */
  private void tickBot(final BrainWiring wiring) {
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
    bb.resetIntent();
    bb.setLastBranch("Idle");
    wiring.brain.tick(bb);
    final Vec3d avoid = this.avoidObstacles.steer(self, snapshot);
    final boolean avoiding = avoid != null;
    final Vec3d move = avoiding ? avoid : bb.intent().clone();
    dampOversteer(move);
    this.ed.setComponent(wiring.botId, new MovementInput(move, new Quatd(), MovementInput.NONE));
    writeDebugSnapshot(wiring.botId, self, target, move, bb.lastBranch(), avoiding);
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
   * there's no target; otherwise {@code 1..12} relative to the bot's forward.
   */
  private void writeDebugSnapshot(
      final EntityId botId,
      final MoverState self,
      final NearbyShip target,
      final Vec3d move,
      final String branch,
      final boolean avoiding) {
    final String effectiveBranch = avoiding ? branch + "+Avoid" : branch;
    final long targetId = target != null ? target.id().getId() : -1L;
    final int clockHour = target != null ? clockHourToTarget(self, target.position()) : 0;
    this.ed.setComponent(
        botId, new BotDebug(effectiveBranch, targetId, move.x, move.z, clockHour));
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

  /** Per-bot brain instance + blackboard, instantiated from the registry archetype. */
  private static final class BrainWiring {
    final EntityId botId;
    final Behavior brain;
    final Blackboard blackboard;

    BrainWiring(
        final EntityId botId,
        final BrainArchetype archetype,
        final BotBrainConfig config,
        final WeaponsFiring firing) {
      this.botId = botId;
      this.brain = archetype.createRoot(config);
      this.blackboard = archetype.createBlackboard(config);
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
