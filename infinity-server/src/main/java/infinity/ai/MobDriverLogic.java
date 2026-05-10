// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

/**
 * Pure-static math kernels extracted from {@link MobDriver}'s update path.
 *
 * <p>Hot-physics code: each method takes its inputs by value (primitives or
 * already-mutable arrays caller owns) and either returns a new value or
 * mutates the caller-owned buffer. No allocation, no instance state, no
 * {@code this} reference. The split shaves cyclomatic complexity off the
 * {@code MobDriver} class without changing any tick-loop behaviour.
 */
final class MobDriverLogic {

    /** {@code 2π} — kept package-private so tests can pin the wrap-around behaviour. */
    static final double TWO_PI = Math.PI * 2;

    /** Probe-driven steering nudge magnitude (radians per tick). */
    private static final double PROBE_TURN_DELTA = 0.05;

    /** Walk threshold below which the mob plays the Idle anim instead. */
    private static final double WALK_SPEED_THRESHOLD = 0.001;

    /** Walk-anim speed multiplier — empirically tuned from observed ground speeds. */
    private static final double WALK_ANIM_SPEED_FACTOR = 1.8 / 1.5;

    private MobDriverLogic() {
        // utility — pure static helpers, do not instantiate.
    }

    /**
     * Move {@code facing} towards {@code targetFacing} by one tick's worth,
     * taking the short way around when the two values are on opposite sides
     * of the {@code 0/2π} seam. Returns the new facing wrapped into
     * {@code [0, 2π)}.
     *
     * <p>Caller is responsible for the early-exit when {@code facing ==
     * targetFacing} so this kernel can stay branchless on the equality
     * check.
     *
     * @param facing current facing (radians)
     * @param targetFacing desired facing (radians, may be on either side of the seam)
     * @param step seconds since last tick
     * @param turnSpeed radians-per-second clamp from {@link MovementSettings}
     * @return new facing in {@code [0, 2π)}
     */
    static double shortestArcFacing(
            final double facing,
            final double targetFacing,
            final double step,
            final double turnSpeed) {
        double next = stepTowards(facing, targetFacing, step * turnSpeed);
        return wrapTwoPi(next);
    }

    /**
     * One-axis short-arc step between two angles, picking the shorter of
     * the two directions when both endpoints are inside {@code [0, 2π)}
     * and falling back to a wrapped step when they straddle the seam.
     */
    private static double stepTowards(
            final double facing, final double targetFacing, final double maxStep) {
        if (facing > targetFacing && facing - targetFacing < Math.PI) {
            // Same domain; target is below current — step negative.
            return Math.max(targetFacing, facing - maxStep);
        }
        if (targetFacing > facing && targetFacing - facing < Math.PI) {
            // Same domain; target is above current — step positive.
            return Math.min(targetFacing, facing + maxStep);
        }
        // Wrapped around 0 — extend the smaller side by 2π and step that way.
        if (facing > targetFacing) {
            return Math.min(targetFacing + TWO_PI, facing + maxStep);
        }
        return Math.max(targetFacing, (facing + TWO_PI) - maxStep);
    }

    /** Wrap an angle into {@code [0, 2π)}; assumes the input is at most one rotation off. */
    static double wrapTwoPi(final double facing) {
        if (facing < 0) {
            return facing + TWO_PI;
        }
        if (facing > TWO_PI) {
            return facing - TWO_PI;
        }
        return facing;
    }

    /**
     * Probe-driven steering: convert a probe-turn signal to a {@code targetFacing}
     * delta. Positive turn → step right (subtract); negative turn → step left
     * (add); zero → no nudge. Magnitude is the fixed {@link #PROBE_TURN_DELTA}.
     */
    static double probeTurnDelta(final double turn) {
        if (turn < 0) {
            return PROBE_TURN_DELTA;
        }
        if (turn > 0) {
            return -PROBE_TURN_DELTA;
        }
        return 0.0;
    }

    /**
     * Holder for {@link #pickAnimAction}'s two-value return. Records the
     * action name and the playback-speed multiplier the rig should run at.
     */
    static final class AnimChoice {
        final String action;
        final double animSpeed;

        AnimChoice(final String action, final double animSpeed) {
            this.action = action;
            this.animSpeed = animSpeed;
        }
    }

    /**
     * Decide which animation to play given the body's current speed.
     * {@code speed > 0.001} → {@code Walk} at a speed-proportional multiplier;
     * otherwise → {@code Idle} at unit speed.
     */
    static AnimChoice pickAnimAction(final double speed) {
        if (Math.abs(speed) > WALK_SPEED_THRESHOLD) {
            return new AnimChoice("Walk", speed * WALK_ANIM_SPEED_FACTOR);
        }
        return new AnimChoice("Idle", 1.0);
    }

    /**
     * Mutate {@code angles} in place: zero pitch + roll, leave yaw alone.
     * Caller already pulled angles out of {@code body.orientation.toAngles}
     * and will write them back via {@code fromAngles} when the mutation
     * actually changes anything ({@code angles[0] != 0 || angles[2] != 0}).
     *
     * @return {@code true} if either pitch or roll was non-zero (i.e. the
     *     mutation needs to be written back)
     */
    static boolean killNonYawAngles(final double[] angles) {
        if (angles[0] == 0 && angles[2] == 0) {
            return false;
        }
        angles[0] = 0;
        angles[2] = 0;
        return true;
    }
}
