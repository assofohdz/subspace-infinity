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
