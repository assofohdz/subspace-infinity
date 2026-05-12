// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Configuration for an entity that periodically spawns other entities; read by spawn-loop systems (today {@code PrizeSystem}).
 *
 * <p>Player-count scaling is additive: effective count = {@code maxCount + countPerPlayer × players},
 * effective radius = base + {@code radiusPerPlayer × players}. {@code 0} on either knob disables scaling.
 * {@code regenBatch} = entities spawned per interval tick once below the effective cap; canon
 * {@code PrizeHideCount}. See {@code player-scaling.md}.
 */
public class Spawner implements EntityComponent {

  private final boolean weighted;
  private final int maxCount;
  private final SpawnType type;
  // Add option to have a spawn interval between spawning, in milliseconds
  private final double spawnInterval;
  // add option to spawn on the radius or in the circle
  private final boolean spawnOnRing;
  // Per-spawner TTL imprinted on spawned entities; 0 = type-specific global default.
  private final long spawnedDecayMillis;
  private final int countPerPlayer;
  private final double radiusPerPlayer;
  private final int regenBatch;
  private final boolean hidden;

  public Spawner() {
    this(0, 0.0, false, null, false, 0L, 0, 0.0, 1, false);
  }

  public Spawner(
      final int maxCount,
      final double spawnInterval,
      final boolean spawnAllOver,
      final SpawnType type,
      final boolean weighted,
      final long spawnedDecayMillis,
      final int countPerPlayer,
      final double radiusPerPlayer,
      final int regenBatch,
      final boolean hidden) {
    this.maxCount = maxCount;
    this.type = type;
    this.spawnInterval = spawnInterval;
    this.spawnOnRing = spawnAllOver;
    this.weighted = weighted;
    this.spawnedDecayMillis = spawnedDecayMillis;
    this.countPerPlayer = countPerPlayer;
    this.radiusPerPlayer = radiusPerPlayer;
    this.regenBatch = regenBatch;
    this.hidden = hidden;
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

  public long getSpawnedDecayMillis() {
    return spawnedDecayMillis;
  }

  public int getCountPerPlayer() {
    return countPerPlayer;
  }

  public double getRadiusPerPlayer() {
    return radiusPerPlayer;
  }

  public int getRegenBatch() {
    return regenBatch;
  }

  public boolean isHidden() {
    return hidden;
  }

  public enum SpawnType {
    Players,
    Prizes
  }
}
