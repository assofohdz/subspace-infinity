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

/** Per-ship MOSS control driver — projects live {@link Thrust}/{@link Speed}/{@link Rotation} onto the {@link RigidBody}. */
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

        if (anyShipStatNull(thrust, speed, rotation, damping, turn)) {
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

        final double shipScale = resolveShipMaxSpeedScale();
        final double accelRate = thrust.getThrust();
        final double maxSpeed = speed.getSpeed() * shipScale;
        final double rotSpeed = rotation.getRadSec();
        final double turnResponsiveness = turn.getRate();

        final Vec3d intent = movementForces.clone();
        final Vec3d currentVel = body.getLinearVelocity();
        clampSpeedToCap(body, currentVel, maxSpeed);

        applyForwardThrust(body, intent, currentVel, accelRate, maxSpeed);
        applyRotationEase(body, intent, rotSpeed, turnResponsiveness, step);
        clampToGameplayPlane(body);
    }

    /**
     * Returns true if any of the five required per-ship stat components is
     * {@code null} — the "not yet configured" signal that
     * {@link #update(long, double)} uses to leave the ship idle until
     * {@code ShipSpawnSystem} projects the stats onto this entity.
     */
    private static boolean anyShipStatNull(
            final Thrust thrust, final Speed speed, final Rotation rotation,
            final LinearDamping damping, final TurnResponsiveness turn) {
        return thrust == null || speed == null || rotation == null
                || damping == null || turn == null;
    }

    /**
     * Slice S1-cal — multiply the raw Subspace velocity-units {@code Speed}
     * value by the engine-tier {@code shipMaxSpeedScale} to land in jME world
     * units / sec. Distinct from the projectile {@code subspaceVelocityScale}
     * (which fits 5000→50 for bullets) because the same fit on ship max-speed
     * felt too fast once LinearDamping 0.99 landed in S1. Default
     * {@code shipMaxSpeedScale 0.01} maps trench warbird's {@code Speed 2000}
     * → 20 jME/sec cap (~40% of bullet velocity). Falls back to
     * {@link EngineConfig#DEFAULTS} when no {@link EngineConfigSystem} is
     * available (test harnesses).
     */
    private double resolveShipMaxSpeedScale() {
        final EngineConfig engineCfg =
            (engineConfigSystem != null) ? engineConfigSystem.get() : EngineConfig.DEFAULTS;
        return engineCfg.shipMaxSpeedScale();
    }

    /**
     * Safety cap — if an external impulse (explosion, bounce) left velocity above
     * {@code maxSpeed}, scale back. Only affects magnitude, so a wall-bounce
     * direction survives. In normal thrusting {@link #applyForwardThrust}'s
     * car-curve gates force to zero before {@code maxSpeed}, so this rarely fires.
     */
    private static void clampSpeedToCap(
            final RigidBody<EntityId, MBlockShape> body,
            final Vec3d currentVel,
            final double maxSpeed) {
        final double currentSpeed = currentVel.length();
        if (currentSpeed > maxSpeed) {
            body.setLinearVelocity(currentVel.mult(maxSpeed / currentSpeed));
        }
    }

    /**
     * Apply this tick's forward-thrust force to {@code body}. Thrust as force —
     * MOSS integrates and handles collision response. Coast decay comes from
     * mphys's linear damping (set on the body upstream); no force-based drag
     * term here.
     *
     * <p>Car-style diminishing acceleration: full force at rest, zero force when
     * velocity-along-thrust reaches {@code maxSpeed}, linear in between. When
     * velocity is against the thrust direction (e.g. after a bounce, or braking
     * from full speed), the factor stays at 1 so we get full acceleration in the
     * new direction. The car-curve gates thrust to zero as the ship approaches
     * {@code maxSpeed}, so steady-state under thrust lands slightly below the
     * cap (always-on damping eats a few percent; see ShipConfig.linearDamping).
     */
    private static void applyForwardThrust(
            final RigidBody<EntityId, MBlockShape> body,
            final Vec3d intent,
            final Vec3d currentVel,
            final double accelRate,
            final double maxSpeed) {
        if (intent.z == 0) {
            return;
        }
        final Vec3d bodyForward = body.orientation.mult(new Vec3d(0, 0, 1));
        final double velAlongForward = currentVel.dot(bodyForward);
        final double progressTowardLimit =
            Math.max(0.0, velAlongForward * Math.signum(intent.z)) / maxSpeed;
        final double factor = Math.max(0.0, 1.0 - progressTowardLimit);
        body.addForce(bodyForward.mult(accelRate * intent.z * factor));
    }

    /**
     * Ease current angular velocity toward target rather than snapping. Gives the
     * ship a sense of mass — small lag entering and exiting turns. Exponential
     * approach is frame-rate independent: {@code t ∈ [0, 1]} is the fraction of
     * the gap to close this tick.
     */
    private static void applyRotationEase(
            final RigidBody<EntityId, MBlockShape> body,
            final Vec3d intent,
            final double rotSpeed,
            final double turnResponsiveness,
            final double step) {
        final double currentAng = body.getRotationalVelocity().y;
        final double targetAng = intent.x * rotSpeed;
        final double t = 1.0 - Math.exp(-turnResponsiveness * step);
        final double newAng = currentAng + (targetAng - currentAng) * t;
        body.setRotationalVelocity(0, newAng, 0);
    }

    /**
     * Snap the body back onto the X/Z gameplay plane. Gameplay is 2D — prevent
     * collision resolution (e.g. teleporting onto a wall, or grazing a block
     * at an oblique angle) from drifting the ship off the plane. Snap Y back
     * each tick and zero any Y-component that accumulated in linear velocity.
     */
    private static void clampToGameplayPlane(final RigidBody<EntityId, MBlockShape> body) {
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
