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

package infinity.es.arena;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/**
 * Marks an entity as representing a loaded arena's map. Carries the map's world-space bounds and
 * the asset-path of the {@code .lvl} file used for rendering. Published by the server's {@code
 * ArenaSystem} when an arena enters {@code LOADED} state, and synced to clients so they can look
 * up the map file for tileset rendering without reading server-only configuration.
 */
public class ArenaMap implements EntityComponent {

  private final Vec3d min;
  private final Vec3d max;
  private final String mapFile;
  private final int arenaIndex;

  // For serialization — defaults that the reflection path overwrites before use.
  public ArenaMap() {
    this.min = new Vec3d();
    this.max = new Vec3d();
    this.mapFile = "";
    this.arenaIndex = 0;
  }

  public ArenaMap(final Vec3d min, final Vec3d max, final String mapFile, final int arenaIndex) {
    this.min = min;
    this.max = max;
    this.mapFile = mapFile;
    this.arenaIndex = arenaIndex;
  }

  public Vec3d getMin() {
    return min;
  }

  public Vec3d getMax() {
    return max;
  }

  /**
   * Asset path of the {@code .lvl} file this arena uses (e.g. {@code "trench.lvl"} or {@code
   * "04-2026-trench/pub2025.lvl"}). Relative to {@code Maps/} on the asset classpath.
   */
  public String getMapFile() {
    return mapFile;
  }

  /**
   * Zero-based slot this arena occupies in the global arena table. Used to derive the per-arena
   * block-type range on both server (when writing tile cells) and client (when registering this
   * arena's tileset). Stable for the arena's lifetime; freed on unload.
   */
  public int getArenaIndex() {
    return arenaIndex;
  }
}
