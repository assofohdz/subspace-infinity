// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code MapFactory.createPrize}. The factory clamps
 * a non-positive {@code decayMillis} up to {@code
 * MapFactory.PRIZE_DEFAULT_DECAY_MS} so a misconfigured Groovy spec can't
 * accidentally produce zero-decay prizes that vanish on the next tick.
 *
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position prize position in world coords
 * @param prizeType prize-type key (matches {@code PrizeTypes.*})
 * @param decayMillis prize lifetime in ms; non-positive clamps to default
 * @param hidden when {@code true}, stamps {@code Hidden} so the client
 *     filters it out of rendering (server-side state unaffected)
 * @param radius collision radius
 */
public record PrizeSpec(
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    String prizeType,
    long decayMillis,
    boolean hidden,
    double radius) {}
