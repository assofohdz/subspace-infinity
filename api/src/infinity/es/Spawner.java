// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * This component enables an entity that spawns different types of entities.
 *
 * @author Asser
 */
public class Spawner implements EntityComponent {

  private final boolean weighted;
  private final int maxCount;
  private final SpawnType type;
  // Add option to have a spawn interval between spawning, in milliseconds
  private final double spawnInterval;
  // add option to spawn on the radius or in the circle
  private final boolean spawnOnRing;
  // Per-spawner TTL imprinted on each spawned entity at creation time. The
  // spawned entity gets a {@link com.simsilica.es.common.Decay} component
  // sized from this; {@code 0} means "use the global default for the entity
  // type the spawner produces" (e.g. {@code GameEntities.PRIZE_DEFAULT_DECAY_MS}
  // for a prize spawner). Stored here rather than on the spawned entity
  // because it's a per-spawner configuration knob, not per-instance state.
  private final long spawnedDecayMillis;

  public Spawner() {
    this(0, 0.0, false, null, false, 0L);
  }

  public Spawner(
      final int maxCount,
      final double spawnInterval,
      final boolean spawnAllOver,
      final SpawnType type,
      final boolean weighted,
      final long spawnedDecayMillis) {
    this.maxCount = maxCount;
    this.type = type;
    this.spawnInterval = spawnInterval;
    this.spawnOnRing = spawnAllOver;
    this.weighted = weighted;
    this.spawnedDecayMillis = spawnedDecayMillis;
  }

  public boolean isWeighted() {
    return weighted;
  }

  public boolean spawnOnRing() {
    return spawnOnRing;
  }

  public double getSpawnInterval() {
    return spawnInterval;
  }

  public int getMaxCount() {
    return maxCount;
  }

  public SpawnType getType() {
    return type;
  }

  /**
   * Per-spawned-entity TTL in milliseconds imprinted by this spawner. Read at
   * spawn time by {@code PrizeSystem} (and any future spawn system) and used
   * to size the {@link com.simsilica.es.common.Decay} component on the
   * spawned entity. {@code 0} means "fall back to the type-specific global"
   * (e.g. {@code GameEntities.PRIZE_DEFAULT_DECAY_MS}).
   */
  public long getSpawnedDecayMillis() {
    return spawnedDecayMillis;
  }

  public enum SpawnType {
    Players,
    Prizes
  }
}
