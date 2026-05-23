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
import infinity.es.BotDebug;
import infinity.es.input.MovementInput;
import infinity.es.ship.BotShip;
import infinity.es.ship.RadarRange;
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

  // Default perception radius (world units) when the bot ship has no RadarRange.
  private static final double DEFAULT_PERCEPTION_RADIUS = 30.0;

  // AvoidObstacles look-ahead corridor (world units). Roughly 5 cells of lookahead at
  // GRID_CELL_SIZE = 1; tune per arena via BotBrainConfig once slice #08 lands.
  private static final double LOOK_AHEAD_DISTANCE = 5.0;

  // Half-width of the avoidance corridor (world units). Wider = avoid earlier;
  // narrower = squeeze through gaps. 0.6 ~= ship-and-a-half.
  private static final double CORRIDOR_HALF_WIDTH = 0.6;

  // AvoidObstacles thrust magnitude when the reactive steer overrides the BT decision.
  private static final double AVOID_THRUST = 1.0;

  private EntityData ed;
  private Perception perception;
  private PhysicsSpace<EntityId, MBlockShape> space;
  private WeaponsFiring firing;
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
    this.brainRegistry.register(new CombatantBrain());
  }

  @Override
  protected void terminate() {
    // brains container started/stopped in start()/stop()
  }

  @Override
  public void start() {
    this.brains = new BrainContainer(this.ed, this.brainRegistry, this.firing);
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
    bb.resetIntent();
    bb.setLastBranch("Idle");
    wiring.brain.tick(bb);
    final Vec3d avoid = this.avoidObstacles.steer(self, snapshot);
    final boolean avoiding = avoid != null;
    final Vec3d move = avoiding ? avoid : bb.intent().clone();
    this.ed.setComponent(wiring.botId, new MovementInput(move, new Quatd(), MovementInput.NONE));
    writeDebugSnapshot(wiring.botId, self, target, move, bb.lastBranch(), avoiding);
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

  /** Read the bot's per-ship RadarRange, or fall back to the system default. */
  private double perceptionRadius(final EntityId botId) {
    final RadarRange rr = this.ed.getComponent(botId, RadarRange.class);
    return (rr != null && rr.getRange() > 0.0) ? rr.getRange() : DEFAULT_PERCEPTION_RADIUS;
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

    BrainWiring(final EntityId botId, final BrainArchetype archetype, final WeaponsFiring firing) {
      this.botId = botId;
      this.brain = archetype.createRoot();
      this.blackboard = archetype.createBlackboard();
      this.blackboard.setSelfId(botId);
      this.blackboard.setFiring(firing);
    }
  }

  /** Per-{@link BotShip} sidecar holding the brain wiring; see entity-containers.md. */
  private static final class BrainContainer extends EntityContainer<BrainWiring> {

    private final BrainRegistry registry;
    private final WeaponsFiring firing;

    BrainContainer(
        final EntityData ed, final BrainRegistry registry, final WeaponsFiring firing) {
      super(ed, BotShip.class);
      this.registry = registry;
      this.firing = firing;
    }

    @Override
    public BrainWiring[] getArray() {
      return super.getArray();
    }

    @Override
    protected BrainWiring addObject(final Entity e) {
      // Slice #08 reads a per-ship BotBrainConfig component to pick the archetype name.
      return new BrainWiring(e.getId(), this.registry.get(CombatantBrain.NAME), this.firing);
    }

    @Override
    protected void updateObject(final BrainWiring wiring, final Entity e) {
      // BotShip is a marker; slice #08 watches BotBrainConfig and re-wires on change.
    }

    @Override
    protected void removeObject(final BrainWiring wiring, final Entity e) {
      // BrainWiring holds no resources to release; let GC clean up.
    }
  }
}
