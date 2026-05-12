// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.specs;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import java.util.Map;

/** Parameter record for {@code MapFactory.createSpawner}; distinct from {@link infinity.config.SpawnerSpec} (the per-arena Groovy spawner record). */
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
