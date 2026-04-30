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
  // type the spawner produces" (e.g. {@code CoreGameConstants.PRIZEDECAY}
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
   * (e.g. {@code CoreGameConstants.PRIZEDECAY}).
   */
  public long getSpawnedDecayMillis() {
    return spawnedDecayMillis;
  }

  public enum SpawnType {
    Players,
    Prizes
  }
}
