// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.Map;

/**
 * Declarative spawner entry from {@code arena.groovy}; materialized by {@code ArenaSystem.doLoad}.
 * Effective count/radius scale additively per player — see {@code player-scaling.md}.
 *
 * <p><b>Subspace {@code [Prize]} canon → Infinity field mapping:</b>
 * <ul>
 *   <li>{@code PrizeFactor} → {@link #countPerPlayer} (canon: {@code count = factor/1000 × players}, purely scaled;
 *       Infinity uses additive {@code maxCount + countPerPlayer × players}; set {@code maxCount=0} for canon-style)</li>
 *   <li>{@code PrizeDelay} (cs) → {@link #spawnIntervalMs} (×10 when porting)</li>
 *   <li>{@code PrizeHideCount} → {@link #regenBatch}</li>
 *   <li>{@code MinimumVirtual} → {@link #radius}</li>
 *   <li>{@code UpgradeVirtual} → {@link #radiusPerPlayer}</li>
 *   <li>(no canon key) → {@link #hidden} (Subspace cloud is hidden-by-default; declarative spawners default visible)</li>
 * </ul>
 *
 * <p>{@code ttlMillis} non-positive falls back to {@link infinity.sim.MapFactory#PRIZE_DEFAULT_DECAY_MS}.
 */
public record SpawnerSpec(
    int x,
    int z,
    double radius,
    int maxCount,
    double spawnIntervalMs,
    long ttlMillis,
    boolean spawnOnRing,
    Map<String, Integer> weightOverrides,
    int countPerPlayer,
    double radiusPerPlayer,
    int regenBatch,
    boolean hidden) {

  /** Compact constructor: defensively copy {@code weightOverrides} so the spec stays immutable. */
  @SuppressWarnings("PMD.UnusedAssignment") // record compact-ctor reassign is the canonical pattern
  public SpawnerSpec {
    weightOverrides =
        weightOverrides == null ? Map.of() : Map.copyOf(weightOverrides);
  }
}
