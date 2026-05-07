// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractControlDriver;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.RigidBody;
import infinity.InfinityConstants;
import infinity.config.EngineConfig;
import infinity.es.input.MovementInput;
import infinity.es.ship.LinearDamping;
import infinity.es.ship.Rotation;
import infinity.es.ship.Speed;
import infinity.es.ship.Thrust;
import infinity.es.ship.TurnResponsiveness;
import infinity.settings.EngineConfigSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-ship control driver invoked by MOSS each physics tick. Reads the ship's
 * current {@link Thrust}, {@link Speed}, and {@link Rotation} ECS components
 * (seeded from the arena's {@code ShipConfig} at spawn time) and projects
 * them onto the MOSS {@link RigidBody}: thrust becomes the acceleration
 * rate, speed is the velocity cap, rotation is the rad/sec scalar for the
 * client's rotation intent.
 *
 * <p>If any of those components are missing on the entity, the ship is left
 * idle — that's the "not yet configured" case (spawn system hasn't projected
 * stats, e.g. because no arena config loaded and no fallback installed).
 *
 * @author Paul Speed (original)
 */
public class PlayerDriver extends AbstractControlDriver<EntityId, MBlockShape> {

    private static final Logger log = LoggerFactory.getLogger(PlayerDriver.class);

    private Vec3d movementForces = new Vec3d();

    private final WatchedEntity shipStats;
    private final EngineConfigSystem engineConfigSystem;

    public PlayerDriver(final EntityId shipEntityId, final EntityData ed,
            final EngineConfigSystem engineConfigSystem) {
        this.shipStats = ed.watchEntity(shipEntityId,
                Thrust.class, Speed.class, Rotation.class,
                LinearDamping.class, TurnResponsiveness.class);
        this.engineConfigSystem = engineConfigSystem;
    }

    public void applyMovementInput(final MovementInput input) {
        movementForces = input.getMove();
        if (log.isTraceEnabled()) {
            log.trace("applyMovementInput({})", input);
        }
    }

    /** Releases the {@link WatchedEntity}. Call when the driver is retired. */
    public void release() {
        shipStats.release();
    }

    @Override
    public void update(final long frameTime, final double step) {
        final RigidBody<EntityId, MBlockShape> body = getBody();
        if (body == null) {
            return;
        }
        // Drivable bodies should not fall asleep.
        body.wakeUp(true);

        final boolean statsChanged = shipStats.applyChanges();
        if (statsChanged && log.isDebugEnabled()) {
            log.debug(
                    "Stats refreshed for entity {}: thrust={} speed={} rotation={} linDamp={} turn={}",
                    shipStats.getId(),
                    shipStats.get(Thrust.class),
                    shipStats.get(Speed.class),
                    shipStats.get(Rotation.class),
                    shipStats.get(LinearDamping.class),
                    shipStats.get(TurnResponsiveness.class));
        }
        final Thrust thrust = shipStats.get(Thrust.class);
        final Speed speed = shipStats.get(Speed.class);
        final Rotation rotation = shipStats.get(Rotation.class);
        final LinearDamping damping = shipStats.get(LinearDamping.class);
        final TurnResponsiveness turn = shipStats.get(TurnResponsiveness.class);

        if (thrust == null || speed == null || rotation == null
                || damping == null || turn == null) {
            // Not yet configured — ShipSpawnSystem hasn't projected stats onto this entity
            // (e.g. no arena config loaded, no fallback installed). Leave ship idle.
            return;
        }

        // Linear damping owns coast decay (mphys integrate applies
        // velocity *= pow(linearDamping, t) per tick). Angular damping = 1.0
        // (off) because PlayerDriver hard-sets rotational velocity each tick
        // from the turnResponsiveness ease below — mphys's default 0.8 angular
        // damping would be noise on top. Re-poke on applyChanges() so live
        // edits to the ship's LinearDamping component take effect; idempotent
        // when the component value hasn't moved.
        if (statsChanged) {
            body.setDamping(damping.getDamping(), 1.0);
        }

        // Slice S1-cal — multiply the raw Subspace velocity-units `Speed`
        // value by the engine-tier shipMaxSpeedScale to land in jME world
        // units / sec. Distinct from the projectile subspaceVelocityScale
        // (which fits 5000→50 for bullets) because the same fit on ship
        // max-speed felt too fast once LinearDamping 0.99 landed in S1.
        // Default `shipMaxSpeedScale 0.01` maps trench warbird's
        // Speed 2000 → 20 jME/sec cap (~40% of bullet velocity). Falls
        // back to EngineConfig.DEFAULTS when no EngineConfigSystem is
        // available (test harnesses).
        final EngineConfig engineCfg =
            (engineConfigSystem != null) ? engineConfigSystem.get() : EngineConfig.DEFAULTS;
        final double shipScale = engineCfg.shipMaxSpeedScale();
        final double accelRate = thrust.getThrust();
        final double maxSpeed = speed.getSpeed() * shipScale;
        final double rotSpeed = rotation.getRadSec();
        final double turnResponsiveness = turn.getRate();

        final Vec3d intent = movementForces.clone();

        // Safety cap — if an external impulse (explosion, bounce) left velocity above
        // maxSpeed, scale back. Only affects magnitude, so a wall-bounce direction survives.
        // In normal thrusting the car-curve below will have already gated the force to zero
        // before we reach maxSpeed, so this branch rarely fires.
        final Vec3d currentVel = body.getLinearVelocity();
        final double currentSpeed = currentVel.length();
        if (currentSpeed > maxSpeed) {
            body.setLinearVelocity(currentVel.mult(maxSpeed / currentSpeed));
        }

        // Thrust as force — MOSS integrates and handles collision response. Coast
        // decay comes from mphys's linear damping (set above); no force-based drag
        // term here. The car-curve gates thrust to zero as the ship approaches
        // maxSpeed, so steady-state under thrust lands slightly below maxSpeed
        // (always-on damping eats a few percent of the cap; documented on
        // ShipConfig.linearDamping).
        if (intent.z != 0) {
            // Car-style diminishing acceleration: full force at rest, zero force when
            // velocity-along-thrust reaches maxSpeed, linear in between. When velocity is
            // against the thrust direction (e.g. after a bounce, or braking from full
            // speed), factor stays at 1 so we get full acceleration in the new direction.
            final Vec3d bodyForward = body.orientation.mult(new Vec3d(0, 0, 1));
            final double velAlongForward = currentVel.dot(bodyForward);
            final double progressTowardLimit =
                Math.max(0.0, velAlongForward * Math.signum(intent.z)) / maxSpeed;
            final double factor = Math.max(0.0, 1.0 - progressTowardLimit);
            body.addForce(bodyForward.mult(accelRate * intent.z * factor));
        }

        // Rotation: ease current angular velocity toward target rather than snapping to
        // it. Gives the ship a sense of mass — small lag entering and exiting turns.
        // Exponential approach is frame-rate independent: t ∈ [0, 1] is the fraction of
        // the gap to close this tick.
        final double currentAng = body.getRotationalVelocity().y;
        final double targetAng = intent.x * rotSpeed;
        final double t = 1.0 - Math.exp(-turnResponsiveness * step);
        final double newAng = currentAng + (targetAng - currentAng) * t;
        body.setRotationalVelocity(0, newAng, 0);

        // Gameplay is 2D on the X/Z plane — prevent collision resolution (e.g. teleporting
        // onto a wall, or grazing a block at an oblique angle) from drifting the ship off the
        // gameplay plane. Snap Y back each tick and zero any Y-component that accumulated in
        // linear velocity.
        if (body.position.y != InfinityConstants.GAMEPLAY_Y) {
            body.position.y = InfinityConstants.GAMEPLAY_Y;
        }
        final Vec3d lv = body.getLinearVelocity();
        if (lv.y != 0) {
            body.setLinearVelocity(new Vec3d(lv.x, 0, lv.z));
        }
    }

    /** Default implementation does nothing. */
    @Override
    public void newContact(final Contact<EntityId, MBlockShape> contact) {
        // no-op
    }
}
