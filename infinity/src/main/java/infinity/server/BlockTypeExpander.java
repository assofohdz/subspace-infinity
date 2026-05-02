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

package infinity.server;

import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.BlockTypeIndex;
import com.simsilica.mblock.phys.Collider;
import com.simsilica.mblock.phys.collision.CubeCollider;
import infinity.InfinityConstants;
import infinity.sim.util.InfinityRunTimeException;
import infinity.systems.MapSystem;
import infinity.map.MapTypes;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side bootstrap helpers for the per-arena tile-block range
 * (block types {@code 100..3139}). Two coordinated steps:
 *
 * <ol>
 *   <li>{@link #expandBlockTypeIndex()} grows {@link BlockTypeIndex} from
 *       Moss's default 82-slot array to {@link InfinityConstants#BLOCK_TYPE_INDEX_SIZE}
 *       so tile types have somewhere to live, filling the new slots with
 *       Moss's {@code INVISIBLE_BLOCK_TYPE} stand-in. Without this,
 *       collider lookups succeed but {@code CubeCollider.getSphereContact}
 *       early-returns because {@code dirMask == 0} — see
 *       {@link com.simsilica.mblock.MaskUtils#recalculateSideMasks}.
 *   <li>{@link #expandCollidersForTiles(Collider[])} installs a unit-cube
 *       collider for every visible tile in every arena slot, leaving
 *       flyover (173–175) and flyunder (176–190) tiles' colliders null so
 *       ships pass through.
 * </ol>
 *
 * <p>Extracted from {@code GameServer} so the bootstrap orchestration stays
 * focused on lifecycle / network setup, while this class owns the tile-range
 * arithmetic and the reflection into {@link BlockTypeIndex}'s static fields.
 * Server-side only; the client's {@code BlockGeometryIndex} performs an
 * analogous (visual) expansion separately.
 */
public final class BlockTypeExpander {

  private static final Logger log = LoggerFactory.getLogger(BlockTypeExpander.class);

  private BlockTypeExpander() {
    // utility class
  }

  /**
   * Expands the {@link BlockTypeIndex} static array to
   * {@link InfinityConstants#BLOCK_TYPE_INDEX_SIZE}, filling tile slots with
   * the {@code INVISIBLE_BLOCK_TYPE} stand-in so {@code MaskUtils} treats
   * them as solid cubes. Idempotent — no-op if the array is already big
   * enough. Uses reflection because {@code BlockTypeIndex} exposes no
   * resize API.
   */
  public static void expandBlockTypeIndex() {
    try {
      final BlockType[] currentTypes = BlockTypeIndex.getTypes();
      if (currentTypes.length >= InfinityConstants.BLOCK_TYPE_INDEX_SIZE) {
        log.info("BlockTypeIndex already has sufficient size: {}", currentTypes.length);
        return;
      }

      final BlockType[] expandedTypes =
          Arrays.copyOf(currentTypes, InfinityConstants.BLOCK_TYPE_INDEX_SIZE);
      final BlockType solidStandin = BlockTypeIndex.get(MapSystem.INVISIBLE_BLOCK_TYPE);
      if (solidStandin == null) {
        throw new InfinityRunTimeException(
            "INVISIBLE_BLOCK_TYPE (" + MapSystem.INVISIBLE_BLOCK_TYPE
                + ") has no registered BlockType; cannot set up tile masks");
      }
      for (int i = InfinityConstants.TILE_TYPE_BASE;
          i < InfinityConstants.BLOCK_TYPE_INDEX_SIZE;
          i++) {
        expandedTypes[i] = solidStandin;
      }

      final Field typesField = BlockTypeIndex.class.getDeclaredField("types");
      typesField.setAccessible(true);
      typesField.set(null, expandedTypes);

      final Field typeCountField = BlockTypeIndex.class.getDeclaredField("typeCount");
      typeCountField.setAccessible(true);
      typeCountField.set(null, InfinityConstants.BLOCK_TYPE_INDEX_SIZE);

      log.info(
          "Expanded BlockTypeIndex from {} to {} types for tile support",
          currentTypes.length,
          InfinityConstants.BLOCK_TYPE_INDEX_SIZE);
    } catch (final Exception e) {
      throw new InfinityRunTimeException("Failed to expand BlockTypeIndex for tiles", e);
    }
  }

  /**
   * Installs colliders for tile block types. Visible tiles get a unit-cube
   * collider; flyover (173-175) and flyunder (176-190) stay null so ships
   * pass through. The same per-tile pattern is repeated for every arena
   * slot ({@code arenaIndex} 0..{@link InfinityConstants#MAX_ARENAS}-1) at
   * offset {@code TILE_TYPE_BASE + arenaIndex * TILE_COUNT}, mirroring the
   * encoding {@code ArenaSystem} uses to allocate tile-type ranges per
   * loaded arena. Without the per-arena loop, ships colliding with a tile
   * in arena 1+ would index off the end of the colliders array (or hit a
   * null collider) and crash in
   * {@code MBlockCollisionSystem.getCollider}.
   *
   * @param baseColliders colliders produced by {@code ColliderFactories}
   *     against the (already-expanded) {@link BlockTypeIndex}; may be
   *     shorter than {@link InfinityConstants#BLOCK_TYPE_INDEX_SIZE} on
   *     the first call
   * @return either {@code baseColliders} (when already sized) or a copy
   *     padded to the full {@code BLOCK_TYPE_INDEX_SIZE} with the per-arena
   *     tile colliders installed
   */
  public static Collider[] expandCollidersForTiles(final Collider[] baseColliders) {
    final Collider[] expanded =
        baseColliders.length >= InfinityConstants.BLOCK_TYPE_INDEX_SIZE
            ? baseColliders
            : Arrays.copyOf(baseColliders, InfinityConstants.BLOCK_TYPE_INDEX_SIZE);

    final Collider solid = new CubeCollider();
    int installed = 0;
    for (int arenaIndex = 0; arenaIndex < InfinityConstants.MAX_ARENAS; arenaIndex++) {
      final int arenaBase =
          InfinityConstants.TILE_TYPE_BASE + arenaIndex * InfinityConstants.TILE_COUNT;
      for (int tileId = 1; tileId <= InfinityConstants.TILE_COUNT; tileId++) {
        final boolean passThrough =
            (tileId >= MapTypes.vieFlyOverStart && tileId <= MapTypes.vieFlyOverEnd)
                || (tileId >= MapTypes.vieFlyUnderStart && tileId <= MapTypes.vieFlyUnderEnd);
        if (!passThrough) {
          expanded[arenaBase + tileId - 1] = solid;
          installed++;
        }
      }
    }
    log.info(
        "Installed {} solid tile colliders across {} arena slots ({}-{} minus flyover/flyunder); array size {}",
        installed,
        InfinityConstants.MAX_ARENAS,
        InfinityConstants.TILE_TYPE_BASE,
        InfinityConstants.BLOCK_TYPE_INDEX_SIZE - 1,
        expanded.length);
    return expanded;
  }
}
