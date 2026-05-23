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
import infinity.ai.steer.AvoidObstacles;
import infinity.ai.steer.PrioritySteering;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Steering;
import infinity.es.input.MovementInput;
import infinity.es.ship.BotShip;
import infinity.es.ship.RadarRange;
import infinity.systems.BaseInfinitySystem;

/**
 * Canonical writer of {@link MovementInput} on {@link BotShip} entities. v1 brain:
 * pursue the nearest enemy ship while avoiding walls + dynamic obstacles via Reynolds
 * corridor projection. Per-bot brain wiring lives in a {@link BrainContainer} — see
 * {@code entity-containers.md} for the canonical pattern. Slice #04 (BT framework)
 * extends the container to watch additional components (BehaviorTree holder, BotBrainConfig).
 * See ADR-0009.
 */
public final class BotBrainSystem extends BaseInfinitySystem {

  // Reynolds lead-prediction window. 0.5s is a good middle ground — long enough to lead
  // a moving target, short enough not to over-shoot when the target turns.
  private static final double LEAD_TIME_SECONDS = 0.5;

  // Default perception radius (world units) when the bot ship has no RadarRange.
  private static final double DEFAULT_PERCEPTION_RADIUS = 30.0;

  // AvoidObstacles look-ahead corridor (world units). Roughly 5 cells of lookahead at
  // GRID_CELL_SIZE = 1; tune per arena via BotBrainConfig once slice #08 lands.
  private static final double LOOK_AHEAD_DISTANCE = 5.0;

  // Half-width of the avoidance corridor (world units). Wider = avoid earlier;
  // narrower = squeeze through gaps. 0.6 ~= ship-and-a-half.
  private static final double CORRIDOR_HALF_WIDTH = 0.6;

  // Rate-shaped intent magnitudes — full forward on pursue, full forward on avoid.
  // BlendedSteering in slice #07 will replace these with weighted contributions.
  private static final double FULL_THRUST = 1.0;

  private EntityData ed;
  private Perception perception;
  private PhysicsSpace<EntityId, MBlockShape> space;
  private BrainContainer brains;

  @Override
  protected void initialize() {
    this.ed = requireSystem(EntityData.class);
    this.perception = requireSystem(Perception.class);
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> physics = requireSystem(MPhysSystem.class);
    this.space = physics.getPhysicsSpace();
  }

  @Override
  protected void terminate() {
    // brains container started/stopped in start()/stop()
  }

  @Override
  public void start() {
    this.brains = new BrainContainer(this.ed);
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
    wiring.pursue.setTarget(pickNearestThreat(self, snapshot));
    final Vec3d intent = wiring.composite.steer(self, snapshot);
    final Vec3d move = (intent != null) ? intent.clone() : new Vec3d();
    this.ed.setComponent(wiring.botId, new MovementInput(move, new Quatd(), MovementInput.NONE));
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

  /**
   * Per-bot steering wiring; brain reconfigures {@link #pursue} target each tick. Holds
   * the bot's {@link EntityId} so per-tick logic can sample state without going through
   * the container.
   */
  private static final class BrainWiring {
    final EntityId botId;
    final Pursue pursue;
    final Steering composite;

    BrainWiring(final EntityId botId) {
      this.botId = botId;
      this.pursue = new Pursue(LEAD_TIME_SECONDS, FULL_THRUST);
      this.composite =
          new PrioritySteering(
              new AvoidObstacles(LOOK_AHEAD_DISTANCE, CORRIDOR_HALF_WIDTH, FULL_THRUST),
              this.pursue);
    }
  }

  /** Per-{@link BotShip} sidecar holding the brain wiring; see entity-containers.md. */
  private static final class BrainContainer extends EntityContainer<BrainWiring> {
    BrainContainer(final EntityData ed) {
      super(ed, BotShip.class);
    }

    @Override
    public BrainWiring[] getArray() {
      return super.getArray();
    }

    @Override
    protected BrainWiring addObject(final Entity e) {
      return new BrainWiring(e.getId());
    }

    @Override
    protected void updateObject(final BrainWiring wiring, final Entity e) {
      // BotShip is a marker — nothing to re-wire on change. Slice #04 will watch
      // BehaviorTree + BotBrainConfig here and swap wiring when they update.
    }

    @Override
    protected void removeObject(final BrainWiring wiring, final Entity e) {
      // BrainWiring holds no resources to release; let GC clean up.
    }
  }
}
