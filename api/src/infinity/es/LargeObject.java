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
 * Marker component routing an entity to the coarse {@code largeStaticBinIndex}
 * instead of the fine bin index. Picked up by the {@code largeEntitySelector}
 * predicate wired into {@code MPhysSystem}, which partitions the population
 * between the two {@code BinEntityManager}s at init.
 *
 * <p>Pair with a {@code SpawnPosition} keyed to the coarse grid
 * ({@code WorldGrids.TILE_GRID}); the per-bin populator query filters on
 * {@code SpawnPosition.binId} using the bin's own grid, so a fine-grid
 * {@code SpawnPosition} would not match any coarse bin.
 *
 * <p>Designed for static structures whose extent spans multiple fine
 * (32-tile) bins but fits inside a single coarse (1024-tile) bin --
 * arena ghost-cubes, large solid bases, multi-tile sensors. Membership
 * across the fine and coarse indexes is disjoint, so contact pairs are
 * unique.
 *
 * @author Asser Fahrenholz
 */
public class LargeObject implements EntityComponent {

    public LargeObject() {
        // marker; no state.
    }

    @Override
    public String toString() {
        return "LargeObject";
    }
}
