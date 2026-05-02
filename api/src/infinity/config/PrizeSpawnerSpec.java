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

package infinity.config;

import java.util.Map;

/**
 * One declarative prize-spawner entry from {@code arena.groovy}'s
 * {@code prizeSpawners { spawn x:..., z:..., ... }} block. Materialized at
 * arena-load time into a real spawner entity by {@code ArenaSystem.doLoad},
 * which translates arena-local {@code (x, z)} into world coords via
 * {@code arenaToWorld} and calls {@code GameEntities.createWeightedPrizeSpawner}.
 *
 * @param x arena-local X (0 = NW corner, 1024 = SE)
 * @param z arena-local Z (0 = NW corner, 1024 = SE)
 * @param radius world-unit radius of the spawn area (prizes drop inside the
 *     disc, or on the ring if {@code spawnOnRing})
 * @param maxCount target number of prizes simultaneously alive from this
 *     spawner — the system keeps spawning (one per {@code spawnIntervalMs})
 *     until this many are alive, then idles until one is acquired or decays
 * @param spawnIntervalMs millis between successive spawn attempts after the
 *     first prize lands; {@code 0} means "respawn as soon as room opens"
 * @param ttlMillis how long each prize lives before its {@code Decay}
 *     component expires; {@code 0} or negative falls back to
 *     {@link infinity.sim.GameEntities#PRIZE_DEFAULT_DECAY_MS}
 * @param spawnOnRing {@code true} = prizes appear on the ring at exactly
 *     {@code radius}; {@code false} = uniformly within the disc
 * @param weightOverrides per-spawner weight overrides on top of the arena's
 *     {@code [PrizeWeight]} defaults. Empty map = "use arena defaults". Keys
 *     are prize-type strings (e.g. {@code "Bomb"}, {@code "Gun"}); values are
 *     non-negative weights. Sparse — entries not listed here keep their arena
 *     default. Stored verbatim for the spawn system to merge at spawn time.
 */
public record PrizeSpawnerSpec(
    int x,
    int z,
    double radius,
    int maxCount,
    double spawnIntervalMs,
    long ttlMillis,
    boolean spawnOnRing,
    Map<String, Integer> weightOverrides) {

  /** Compact constructor: defensively copy {@code weightOverrides} so the spec stays immutable. */
  @SuppressWarnings("PMD.UnusedAssignment") // record compact-ctor reassign is the canonical pattern
  public PrizeSpawnerSpec {
    weightOverrides =
        weightOverrides == null ? Map.of() : Map.copyOf(weightOverrides);
  }
}
