// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Marker component identifying an entity as a <b>sensor body</b>: a static physics
 * shape that should generate {@code newContact} events for downstream listeners
 * (e.g. arena membership, safe-zones, gravity wells) but must <i>never</i> participate
 * in collision response. {@code ContactSystem} recognises sensor entities and calls
 * {@code Contact.disable()} before the resolver sees them, so the impulse channel
 * is short-circuited and the dynamic body's motion is unaffected.
 *
 * <p>MOSS has no first-class sensor API; this is the canonical Moss-style ghost
 * pattern (per Paul Speed's 2023 guidance and the prior art in
 * {@code infinity.sim.CubeFactory.createStaticGhostCube}). Pair the component with
 * a static body and a {@code ShapeInfo} that resolves to a non-rendered shape.
 *
 * <p>Listeners discriminate sensor kinds by the entity's other components — e.g.
 * an arena sensor also carries {@code ArenaId} + {@code ArenaMap}. There is no
 * "kind" field on the marker; add one only if a routing case can't be expressed
 * via the entity's existing components.
 *
 * @author Asser Fahrenholz
 */
public class Sensor implements EntityComponent {

    public Sensor() {
        // marker; no state.
    }

    @Override
    public String toString() {
        return "Sensor";
    }
}
