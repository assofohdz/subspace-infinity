// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.Map;

/** Declarative spawner entry from {@code arena.groovy}; materialized by {@code ArenaSystem.doLoad}. Effective count/radius scale additively per player. Diverges from {@code [Prize]} canon — see {@code player-scaling.md}. */
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
