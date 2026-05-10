// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import java.util.Map;

/**
 * Parameter record for {@code MapFactory.createSpawner}. Carved out of
 * the legacy 15-positional-arg signature (BACKLOG #2 — too many positional
 * args). Named {@code SpawnerCreateSpec} to avoid a clash with the
 * pre-existing {@code infinity.config.SpawnerSpec} (the per-arena Groovy
 * spawner record), which is read by callers like {@code ArenaLogic}
 * alongside this factory-input record.
 *
 * @param owner currently unused by the factory; preserved for future
 *     parent-linkage symmetry
 * @param phys physics space the spawn position is relative to
 * @param createdTime ns spawn time matching {@code SimTime#getTime}
 * @param position spawner position in world coords
 * @param spawnInterval seconds between spawn ticks
 * @param spawnOnRing when {@code true}, prizes spawn on the spawner's
 *     ring; when {@code false}, uniform-disc within {@code radius}
 * @param radius spawner area radius
 * @param maxCount base number of prizes alive at once. Effective cap is
 *     {@code maxCount + countPerPlayer × playersInArena}
 * @param prizeDecayMillis per-prize TTL stored in the {@code Spawner}'s
 *     {@code spawnedDecayMillis} field; non-positive = "use the global
 *     {@code PRIZE_DEFAULT_DECAY_MS}"
 * @param weightOverrides per-spawner prize-type weight overrides. Empty
 *     map = "no overrides; use arena defaults"
 * @param countPerPlayer additive count scaling per active player in the
 *     spawner's arena; {@code 0} disables count scaling
 * @param radiusPerPlayer additive radius scaling per active player, in
 *     world units; {@code 0.0} disables radius scaling
 * @param regenBatch number of prizes to spawn per {@code spawnInterval}
 *     when below the effective cap; {@code 1} preserves pre-Slice-8d
 *     cadence
 * @param hidden when {@code true}, spawned prizes get a {@code Hidden}
 *     marker so the client doesn't render them
 */
public record SpawnerCreateSpec(
    EntityId owner,
    PhysicsSpace<?, ?> phys,
    long createdTime,
    Vec3d position,
    double spawnInterval,
    boolean spawnOnRing,
    double radius,
    int maxCount,
    long prizeDecayMillis,
    Map<String, Integer> weightOverrides,
    int countPerPlayer,
    double radiusPerPlayer,
    int regenBatch,
    boolean hidden) {}
