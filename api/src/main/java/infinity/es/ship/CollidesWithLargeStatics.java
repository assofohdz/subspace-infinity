// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Opt-in marker telling moss's PhysicsSpace to include this body in the
 * coarse large-static contact pass. Bodies without the marker skip the entire
 * coarse pass before narrow phase, which saves (non-ship-dynamic-bodies ×
 * loaded-arenas) discarded contact pairs per frame.
 *
 * <p>Today only ships need this marker — they're the only counter-party
 * {@code ArenaMembershipSystem.newContact} acts on, and the only
 * {@code LargeObject}s in flight are arena ghost-cubes. Projectiles, mobs
 * without {@code ShipType}, and other dynamic bodies should NOT receive this
 * marker so their coarse-pass contacts get filtered out by
 * {@code PhysicsSpace.setLargeStaticCollisionFilter}.
 *
 * <p>Reconsider if a future {@code LargeObject} (e.g. a solid wall, not a
 * sensor) needs projectiles to participate — at that point the marker becomes
 * "this body cares about large structures in general" and projectiles get it.
 *
 * Hot-path note: Predicate runs per active body per frame; component
 * lookup is HashMap-ish so it's fine at thousands of bodies.
 * If profiling flags it, cache the answer at body-creation time —
 * `InfinityEntityBodyFactory.createRigidBody(id)` reads the marker once
 * and stashes a `boolean` on a `RigidBody` subclass; the predicate becomes
 * `body -> ((MyBody) body).collidesLarge`, a field read.
 */
public class CollidesWithLargeStatics implements EntityComponent {
  public CollidesWithLargeStatics() {
    // empty constructor for serialization
  }
}
