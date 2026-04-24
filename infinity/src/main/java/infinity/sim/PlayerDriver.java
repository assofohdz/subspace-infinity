/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
import infinity.es.input.MovementInput;
import infinity.es.ship.Rotation;
import infinity.es.ship.Speed;
import infinity.es.ship.Thrust;
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

    /**
     * Fraction of {@code Thrust} applied as drag force when no thrust intent is
     * given. {@code 0} = pure coast (no drag), {@code 1} = decelerate as fast as
     * thrust accelerates. {@code 0.25} gives a gentle slowdown. Tune per feel.
     */
    // TODO(physics-tune): promote DRAG_FACTOR to a ShipStat / Groovy field once the
    // canonical value for each ship is known.
    private static final double DRAG_FACTOR = 0.05;

    /**
     * How fast the ship's angular velocity approaches the target rotation rate.
     * Higher = snappier turn response, lower = more sluggish/heavy ship feel.
     * Frame-rate independent (used as the rate constant in an exponential approach).
     * {@code 8.0} reaches ~95% of target rotation in ~0.4 seconds.
     */
    // TODO(physics-tune): promote TURN_RESPONSIVENESS to a ShipStat / Groovy field
    // once different per-ship feels are needed.
    private static final double TURN_RESPONSIVENESS = 8.0;

    private Vec3d movementForces = new Vec3d();

    private final WatchedEntity shipStats;

    public PlayerDriver(final EntityId shipEntityId, final EntityData ed) {
        this.shipStats = ed.watchEntity(shipEntityId,
                Thrust.class, Speed.class, Rotation.class);
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

        if (shipStats.applyChanges()) {
            log.info(
                    "Stats refreshed for entity {}: thrust={} speed={} rotation={}",
                    shipStats.getId(),
                    shipStats.get(Thrust.class),
                    shipStats.get(Speed.class),
                    shipStats.get(Rotation.class));
        }
        final Thrust thrust = shipStats.get(Thrust.class);
        final Speed speed = shipStats.get(Speed.class);
        final Rotation rotation = shipStats.get(Rotation.class);

        if (thrust == null || speed == null || rotation == null) {
            // Not yet configured — ShipSpawnSystem hasn't projected stats onto this entity
            // (e.g. no arena config loaded, no fallback installed). Leave ship idle.
            return;
        }

        final double accelRate = thrust.getThrust();
        final double maxSpeed = speed.getSpeed();
        final double rotSpeed = rotation.getRadSec();

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

        // Thrust / drag as forces — MOSS integrates and handles collision response.
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
        } else if (currentSpeed > 0.001) {
            // Drag: force opposite to current motion, magnitude = accelRate × DRAG_FACTOR.
            final Vec3d dragDir = currentVel.mult(-1.0 / currentSpeed);
            body.addForce(dragDir.mult(accelRate * DRAG_FACTOR));
        }

        // Rotation: ease current angular velocity toward target rather than snapping to
        // it. Gives the ship a sense of mass — small lag entering and exiting turns.
        // Exponential approach is frame-rate independent: t ∈ [0, 1] is the fraction of
        // the gap to close this tick.
        final double currentAng = body.getRotationalVelocity().y;
        final double targetAng = intent.x * rotSpeed;
        final double t = 1.0 - Math.exp(-TURN_RESPONSIVENESS * step);
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
