// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.Map;

/**
 * One declarative spawner entry from {@code arena.groovy}'s
 * {@code spawners { spawn x:..., z:..., ... }} block. Materialized at
 * arena-load time into a real spawner entity by {@code ArenaSystem.doLoad},
 * which translates arena-local {@code (x, z)} into world coords via
 * {@code arenaToWorld} and calls {@code GameEntities.createSpawner}.
 *
 * <h2>Subspace canon divergence</h2>
 *
 * <p>Slice 8d (C2) absorbs the <em>concepts</em> of Subspace's arena-global
 * {@code [Prize]} hidden-prize cloud knobs into this per-spawner DSL rather
 * than wiring those keys through {@code PrizeConfig}. Operators porting a
 * Subspace map can express the same behaviour by authoring one large
 * {@code spawners { spawn ... }} entry centred on the arena. The mapping:
 *
 * <table>
 *   <caption>Subspace {@code [Prize]} canon → Infinity {@link SpawnerSpec}</caption>
 *   <tr><th>Subspace key</th><th>Infinity field</th><th>Note</th></tr>
 *   <tr><td>{@code PrizeFactor}</td><td>{@link #countPerPlayer}</td>
 *       <td>Canon: {@code count = factor / 1000 × players} (purely scaled).
 *           Infinity uses additive ({@link #maxCount} + {@code countPerPlayer × players});
 *           set {@code maxCount=0} for canon-style purely-scaled behaviour.</td></tr>
 *   <tr><td>{@code PrizeDelay}</td><td>{@link #spawnIntervalMs}</td>
 *       <td>Canon is centiseconds; convert ×10 to ms when porting.</td></tr>
 *   <tr><td>{@code PrizeHideCount}</td><td>{@link #regenBatch}</td>
 *       <td>Semantic match: prizes regenerated per interval.</td></tr>
 *   <tr><td>{@code MinimumVirtual}</td><td>{@link #radius}</td>
 *       <td>Semantic match: base spawn-area radius from arena centre.</td></tr>
 *   <tr><td>{@code UpgradeVirtual}</td><td>{@link #radiusPerPlayer}</td>
 *       <td>Semantic match: extra radius per active player.</td></tr>
 *   <tr><td>(no canon key)</td><td>{@link #hidden}</td>
 *       <td>Infinity-specific. Subspace cloud is hidden-by-default; declarative
 *           spawners default to visible.</td></tr>
 * </table>
 *
 * <p>The Subspace keys themselves stay unwired — REFERENCE.md gap is
 * documented here. See {@code .claude/rules/player-scaling.md} for the
 * additive-scaling pattern and {@code .scratch/settings-pipeline-slices.md}
 * Slice 8d for the rationale.
 *
 * @param x arena-local X (0 = NW corner, 1024 = SE)
 * @param z arena-local Z (0 = NW corner, 1024 = SE)
 * @param radius world-unit base radius of the spawn area (prizes drop inside
 *     the disc, or on the ring if {@code spawnOnRing}). Effective radius =
 *     {@code radius + radiusPerPlayer × playersInArena}.
 * @param maxCount base number of prizes simultaneously alive from this
 *     spawner. Effective cap = {@code maxCount + countPerPlayer × playersInArena}.
 *     The spawn loop keeps spawning (one batch per {@code spawnIntervalMs})
 *     until this cap is reached, then idles until one is acquired or decays.
 * @param spawnIntervalMs millis between successive spawn batches after the
 *     first prize lands; {@code 0} means "respawn as soon as room opens"
 * @param ttlMillis how long each prize lives before its {@code Decay}
 *     component expires; {@code 0} or negative falls back to
 *     {@link infinity.sim.GameEntities#PRIZE_DEFAULT_DECAY_MS}
 * @param spawnOnRing {@code true} = prizes appear on the ring at exactly
 *     the effective radius; {@code false} = uniformly within the disc
 * @param weightOverrides per-spawner weight overrides on top of the arena's
 *     {@code [PrizeWeight]} defaults. Empty map = "use arena defaults". Keys
 *     are prize-type strings (e.g. {@code "Bomb"}, {@code "Gun"}); values are
 *     non-negative weights. Sparse — entries not listed here keep their arena
 *     default. Stored verbatim for the spawn system to merge at spawn time.
 * @param countPerPlayer additive per-player count scaling. {@code 0}
 *     (default) disables scaling. See class-level table for canon mapping.
 * @param radiusPerPlayer additive per-player radius scaling, in world units.
 *     {@code 0.0} (default) disables scaling.
 * @param regenBatch number of prizes to spawn per {@code spawnIntervalMs}
 *     tick when below the effective cap. {@code 1} (default) preserves the
 *     pre-Slice-8d "spawn one at a time" cadence.
 * @param hidden when {@code true}, spawned prizes carry an
 *     {@code infinity.es.Hidden} marker so the client doesn't render them.
 *     Server-side collision / pickup / applier dispatch are unaffected — the
 *     prize still exists and ships still bump into it. Defaults to
 *     {@code false}.
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
