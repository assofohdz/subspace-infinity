// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;

/**
 * Parameter record for {@code ShipFactory.createShip} and {@code
 * ShipFactory.createPlayerShip}. Both methods share the same input shape;
 * {@code createPlayerShip} composes the player-only components on top of
 * {@code createShip}.
 *
 * <p>Carved out of the legacy 7-positional-arg signature (BACKLOG #2 — too
 * many positional args) so call sites read like keyword args.
 *
 * @param spawnLoc world spawn position
 * @param owner parent entity (player container) for {@code Parent}
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param ship ship type id (matches {@link infinity.Ship}'s byte id)
 * @param radius collision radius (typically threaded from
 *     {@code engineConfigSystem.get().shipRadius()}; module/test callers
 *     can pass {@code EngineConfig.DEFAULTS.shipRadius()})
 */
public record ShipSpec(
    Vec3d spawnLoc,
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    byte ship,
    double radius) {}
