// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Rayd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.BlockColliderIterator;
import com.simsilica.mblock.phys.MBlockCollisionSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.QueryFilter;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.SphereVolume;
import com.simsilica.mworld.BlockIterator;
import infinity.InfinityConstants;
import infinity.es.Bounty;
import infinity.es.Dead;
import infinity.es.Frequency;
import infinity.es.ship.BotShip;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.PlayerShip;
import infinity.config.ZoneBotAiConfig;
import infinity.settings.ZoneBotAiConfigSystem;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server-side {@link Perception} implementation. Dynamic bodies (ships, asteroids,
 * projectiles) come from {@code physicsSpace.queryBounds(SphereVolume, QueryFilter)} —
 * mphys handles the broadphase. Static-wall avoidance uses
 * {@code MBlockCollisionSystem.rayIterator} — proper DDA traversal of the voxel grid
 * with collider filtering, far better than discrete cell-grid sampling.
 */
public final class PerceptionService extends BaseInfinitySystem implements Perception {

  // Forward-ray lookahead + obstacle radius now live on ZoneBotAiConfig — see v3 #02.C.
  // lookAheadDistance is shared with BotBrainSystem's AvoidObstacles (consolidated).

  // Y level of the ray cast against the voxel world. Walls placed by
  // LegacyMapProjector occupy cell Y=1 → world Y in [1.0, 2.0]. GAMEPLAY_Y (1.0) is the
  // bottom edge of that range — ambiguous in DDA traversal. Centre the ray inside the
  // wall row instead. See the "ship sphere vs wall block Y misalignment" entry in
  // .scratch/code-todos-backlog.md for the underlying placement issue.
  private static final double WALL_RAY_Y = InfinityConstants.GAMEPLAY_Y + 0.5;

  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> space;
  private MBlockCollisionSystem<EntityId> blockCollision;
  private ZoneBotAiConfigSystem zoneBotAi;

  @Override
  protected void initialize() {
    this.ed = requireSystem(EntityData.class);
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> physics = requireSystem(MPhysSystem.class);
    this.space = physics.getPhysicsSpace();
    this.zoneBotAi = requireSystem(ZoneBotAiConfigSystem.class);
    // MBlockCollisionSystem.rayIterator is the proper voxel-world ray API; the base
    // CollisionSystem.queryWorldHits is stubbed upstream for MBlock worlds.
    this.blockCollision = (MBlockCollisionSystem<EntityId>) this.space.getCollisionSystem();
  }

  @Override
  protected void terminate() {
    // no held resources
  }

  @Override
  public PerceptionSnapshot perceive(
      final EntityId bot, final MoverState self, final double radius) {
    final Frequency botFreqComp = this.ed.getComponent(bot, Frequency.class);
    final int botFreq = botFreqComp != null ? botFreqComp.getFrequency() : -1;

    final List<NearbyShip> threats = new ArrayList<>();
    final List<NearbyShip> allies = new ArrayList<>();
    final List<NearbyObstacle> obstacles = new ArrayList<>();

    classifyBodiesInRadius(bot, self, radius, botFreq, threats, allies, obstacles);
    castForwardWallRay(self, obstacles);

    return new PerceptionSnapshot(
        List.copyOf(threats), List.copyOf(allies), List.copyOf(obstacles));
  }

  /**
   * Query the mphys broadphase for active bodies in {@code radius}; classify each as
   * threat / ally (ships) or obstacle (everything else). Dead entities + the bot itself
   * are skipped.
   */
  private void classifyBodiesInRadius(
      final EntityId bot,
      final MoverState self,
      final double radius,
      final int botFreq,
      final List<NearbyShip> threats,
      final List<NearbyShip> allies,
      final List<NearbyObstacle> obstacles) {
    final SphereVolume volume = new SphereVolume(self.position(), radius);
    final QueryFilter<EntityId, MBlockShape> filter = new QueryFilter<>(QueryFilter.TYPE_ACTIVE);
    final Collection<AbstractBody<EntityId, MBlockShape>> bodies =
        this.space.queryBounds(volume, filter);
    for (final AbstractBody<EntityId, MBlockShape> body : bodies) {
      if (body.id.equals(bot)) {
        continue;
      }
      if (this.ed.getComponent(body.id, Dead.class) != null) {
        continue;
      }
      classifyOneBody(body, botFreq, threats, allies, obstacles);
    }
  }

  /**
   * Dispatch a single body into the right list. Ships (carrying {@link BotShip} or
   * {@link PlayerShip}) become {@link NearbyShip} entries — team-mate frequency goes to
   * allies, mismatch to threats. Everything else lands in obstacles.
   */
  private void classifyOneBody(
      final AbstractBody<EntityId, MBlockShape> body,
      final int botFreq,
      final List<NearbyShip> threats,
      final List<NearbyShip> allies,
      final List<NearbyObstacle> obstacles) {
    final boolean isShip =
        this.ed.getComponent(body.id, BotShip.class) != null
            || this.ed.getComponent(body.id, PlayerShip.class) != null;
    if (isShip) {
      final Frequency otherFreqComp = this.ed.getComponent(body.id, Frequency.class);
      final int otherFreq = otherFreqComp != null ? otherFreqComp.getFrequency() : -1;
      final NearbyShip ship = buildNearbyShip(body, otherFreq);
      if (otherFreq == botFreq) {
        allies.add(ship);
      } else {
        threats.add(ship);
      }
    } else {
      obstacles.add(new NearbyObstacle(body.position.clone(), body.shape.getMass().getRadius()));
    }
  }

  /** Snapshot a ship body into a {@link NearbyShip}, sampling its energy% + bounty for ADR-0016 inputs. */
  private NearbyShip buildNearbyShip(
      final AbstractBody<EntityId, MBlockShape> body, final int freq) {
    final Vec3d velocity =
        (body instanceof RigidBody)
            ? ((RigidBody<EntityId, MBlockShape>) body).getLinearVelocity().clone()
            : new Vec3d();
    final Energy energy = this.ed.getComponent(body.id, Energy.class);
    final EnergyStats energyStats = this.ed.getComponent(body.id, EnergyStats.class);
    final double energyPct =
        (energy != null && energyStats != null && energyStats.max() > 0)
            ? (double) energy.getEnergy() / energyStats.max()
            : -1.0;
    final Bounty bountyComp = this.ed.getComponent(body.id, Bounty.class);
    final int bounty = bountyComp != null ? bountyComp.getBounty() : 0;
    return new NearbyShip(
        body.id, body.position.clone(), body.orientation.clone(), velocity, freq, energyPct, bounty);
  }

  /**
   * Cast a forward ray against the voxel world; first collider-confirmed hit becomes a
   * synthetic {@link NearbyObstacle} at the world hit-point. {@link infinity.ai.steer.AvoidObstacles}'s
   * Reynolds corridor projection then handles walls uniformly with dynamic bodies.
   *
   * <p>The ray is intentionally decoupled from the ship's Y: walls are placed at
   * cell Y = {@link InfinityConstants#GAMEPLAY_Y} (see {@code LegacyMapProjector}), so
   * we force ray origin Y to {@code GAMEPLAY_Y} and zero out any Y component on the
   * forward direction. This makes the raycast immune to orientation pitch / roll drift
   * that would otherwise tilt the ray out of the wall plane over the lookahead distance.
   */
  private void castForwardWallRay(final MoverState self, final List<NearbyObstacle> obstacles) {
    final Vec3d forward = self.orientation().mult(Vec3d.UNIT_Z);
    forward.y = 0.0;
    if (forward.lengthSq() < 1e-9) {
      // Degenerate — orientation aimed straight up/down. Nothing to ray-cast.
      return;
    }
    forward.normalizeLocal();
    final Vec3d origin = new Vec3d(self.position().x, WALL_RAY_Y, self.position().z);
    final Rayd ray = new Rayd(origin, forward);
    final ZoneBotAiConfig cfg = this.zoneBotAi.get();
    final BlockColliderIterator hits = this.blockCollision.rayIterator(ray, cfg.lookAheadDistance());
    if (hits.hasNext()) {
      final BlockIterator.Intersection hit = hits.next();
      obstacles.add(new NearbyObstacle(hit.getPoint(), cfg.wallObstacleRadius()));
    }
  }
}
