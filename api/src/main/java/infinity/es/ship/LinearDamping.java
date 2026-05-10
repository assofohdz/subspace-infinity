// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship <b>linear-damping</b> coefficient passed to mphys's
 * {@code RigidBody.setDamping(linear, angular)}. The integrator applies
 * {@code velocity *= pow(damping, t)} per physics tick (where {@code t} is
 * the post-temperature timestep).
 *
 * <p>{@code 1.0} = no damping (Subspace-canonical glide); {@code 0.99} = 1%
 * velocity loss per second at typical operating speed; {@code 0.9} = mphys
 * default (energetic decay, feels like air resistance). Project at spawn
 * from the per-arena {@code ShipConfig.linearDamping()}.
 *
 * <p>Not part of the canonical Subspace {@code [Ship]} INI — Subspace ships
 * have no drag (speed cap is the only ceiling). Infinity uses a small
 * always-on damping for arcade feel; the value is documented as a deliberate
 * Infinity extension on {@code ShipConfig}.
 *
 * <p>Server-only — {@code PlayerDriver} reads this and pokes the live body's
 * damping on the next {@code applyChanges()} tick. Does not cross the wire;
 * not registered in {@code GameServer.registerSerializers()}.
 *
 * @author Asser Fahrenholz
 */
public class LinearDamping implements EntityComponent {

    private final double damping;

    public LinearDamping() {
        this(1.0);
    }

    public LinearDamping(final double damping) {
        this.damping = damping;
    }

    public double getDamping() {
        return damping;
    }

    @Override
    public String toString() {
        return "LinearDamping[" + damping + "]";
    }
}
