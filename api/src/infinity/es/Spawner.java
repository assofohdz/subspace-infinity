// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Configuration for an entity that periodically spawns other entities. Read by
 * the spawn-loop systems (today: {@code PrizeSystem} for {@link
 * SpawnType#Prizes}).
 *
 * <p>Slice 8d (C2) added four scaling/visibility fields. The math is
 * additive — see the field-level Javadoc for the formula.
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

  /**
   * Additive count scaling per active player in the spawner's arena.
   * Effective max = {@link #getMaxCount} + {@code countPerPlayer × players}.
   * {@code 0} (default) disables scaling — a flat {@link #getMaxCount} cap.
   * See {@code .claude/rules/player-scaling.md}.
   */
  public int getCountPerPlayer() {
    return countPerPlayer;
  }

  /**
   * Additive radius scaling per active player in the spawner's arena.
   * Effective radius = base + {@code radiusPerPlayer × players}, where base
   * comes from the spawner entity's {@code SphereShape}. {@code 0.0}
   * (default) disables scaling. See {@code .claude/rules/player-scaling.md}.
   */
  public double getRadiusPerPlayer() {
    return radiusPerPlayer;
  }

  /**
   * Number of entities to spawn per {@link #getSpawnInterval} tick once the
   * effective max count isn't yet met. {@code 1} (default) preserves the
   * pre-Slice-8d "spawn one at a time" cadence; higher values let an arena
   * burst-regenerate prize stocks (canonical Subspace
   * {@code [Prize] PrizeHideCount}). The spawn loop never exceeds the
   * effective max — partial batches happen at the cap boundary.
   */
  public int getRegenBatch() {
    return regenBatch;
  }

  /**
   * When {@code true}, spawned entities receive an {@link Hidden} marker so
   * the client filters them out of rendering. Server-side state (collision,
   * pickup, applier dispatch, decay) is unaffected — the prize still exists
   * and ships still bump into it. Defaults to {@code false}.
   */
  public boolean isHidden() {
    return hidden;
  }

  public enum SpawnType {
    Players,
    Prizes
  }
}
