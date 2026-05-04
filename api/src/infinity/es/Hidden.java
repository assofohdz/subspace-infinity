// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Marker component meaning "the client should not render this entity."
 * Empty by design — presence is the signal.
 *
 * <p>Today's only producer is {@code PrizeSystem} for spawners declared
 * {@code hidden: true} in {@code arena.groovy}'s {@code spawners} block —
 * the server keeps the prize fully alive (collisions, pickup, applier
 * dispatch) but the client filters it out of the visible scene. Reusable for
 * forthcoming mechanics that need the same "exists server-side, invisible
 * client-side" contract (ship cloaking, decoys).
 *
 * <p>Crosses the wire — registered in {@code GameServer.registerSerializers}.
 * Client-side filter lives in {@code ModelContainer.addObject}: entities
 * carrying {@code Hidden} are tracked in the container (so add/remove edges
 * stay clean) but no spatial is bound.
 */
public class Hidden implements EntityComponent {

  public Hidden() {}
}
