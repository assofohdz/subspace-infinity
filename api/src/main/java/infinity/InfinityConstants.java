/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity;

import com.simsilica.ethereal.net.ObjectStateProtocol;
import com.simsilica.ethereal.zone.ZoneGrid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.bits.QuatBits;
import com.simsilica.mathd.bits.Vec3Bits;
import com.simsilica.mworld.WorldGrids;

/** Game setup constants (name, version, network protocol parameters, world grid sizes). */
public class InfinityConstants {

  private InfinityConstants() {
  }

  public static final String NAME = "Subspace Infinity";
  public static final int PROTOCOL_VERSION = 42;

  public static final int DEFAULT_PORT = 6942;

  /**
   * We add an extra channel on the client-server connection to send chat related messages. This is
   * its own separate TCP socket that avoids tying up the main connection.
   */
  public static final int CHAT_CHANNEL = 0;

  /**
   * Send entity-related messages over a separate channel to avoid clogging up the main channels.
   */
  public static final int ES_CHANNEL = 1;

  /** Send terrain related messages over a separate channel. */
  public static final int TERRAIN_CHANNEL = 2;

  /**
   * Size of a world/leaf grid cell for this project. This is the project's source of truth;
   * {@link WorldGrids#LEAF_SIZE} is only the MOSS default and may be overridden here without
   * touching MOSS. In-project code should reference this constant rather than {@code
   * WorldGrids.LEAF_SIZE} so the override stays honest.
   */
  public static final int GRID_CELL_SIZE = WorldGrids.LEAF_SIZE;

  /**
   * Size (in world units) of one arena tile — i.e. one {@code TileId} footprint on MOSS's
   * {@code TILE_GRID}. Source of truth for the project; defaults to {@link WorldGrids#TILE_SIZE}
   * but can be overridden here. Same rationale as {@link #GRID_CELL_SIZE} — in-project code should
   * not reach into {@code WorldGrids} for size constants.
   */
  public static final int TILE_SIZE = WorldGrids.TILE_SIZE;

  /**
   * Maximum number of concurrently-loaded arenas. Fixes the size of the per-arena block-type slot
   * table in {@code BlockGeometryIndex} and bounds the slot allocator in {@code ArenaSystem}.
   * Tiles use ({@link #TILE_COUNT} * MAX_ARENAS) block-type indices above {@link #TILE_TYPE_BASE};
   * staying well inside the 20-bit cell-type field ({@code MaskUtils.TYPE_MASK = 0x000fffff},
   * ~1M slots).
   */
  public static final int MAX_ARENAS = 16;

  /**
   * Base block-type index for flat 2D Subspace tiles. Tile slots start here and run for
   * {@link #MAX_ARENAS} × {@link #TILE_COUNT} contiguous entries — each arena getting its own
   * {@code [TILE_TYPE_BASE + arenaIndex * TILE_COUNT, ... + TILE_COUNT - 1]} range. Block types
   * 0..{@code TILE_TYPE_BASE - 1} stay reserved for non-tile blocks.
   */
  public static final int TILE_TYPE_BASE = 100;

  /** Total number of tiles in the Subspace tileset (190 visible IDs, 1..190). */
  public static final int TILE_COUNT = 190;

  /**
   * Required size for the BlockTypeIndex array — covers non-tile block types up to
   * {@link #TILE_TYPE_BASE} plus per-arena tile ranges. Matches the encoding in
   * {@code ArenaSystem}'s arena-slot allocator and is the single source of truth for both the
   * server-side collider array ({@code GameServer.expandCollidersForTiles}) and the client-side
   * {@code BlockGeometryIndex}.
   */
  public static final int BLOCK_TYPE_INDEX_SIZE = TILE_TYPE_BASE + MAX_ARENAS * TILE_COUNT;

  /**
   * Block-type index for invisible physics blocks. These cells have collision but no visible
   * geometry — used as a fallback for unrecognised lvl tile ids and as the solid stand-in
   * for the per-arena tile-block range. Must match
   * {@code BlockGeometryIndex.INVISIBLE_BLOCK_TYPE_INDEX} on the client.
   */
  public static final int INVISIBLE_BLOCK_TYPE = 11;

  /**
   * Block-type index for light-emitter cells. Non-solid, transparent, invisible; the
   * {@code BlockType}'s emission is picked up by {@code LightUtils.recalculateLighting}'s
   * flood fill. Must match {@code BlockGeometryIndex.LIGHT_EMITTER_BLOCK_TYPE_INDEX} on
   * the client.
   */
  public static final int LIGHT_EMITTER_BLOCK_TYPE = 12;

  /** Maximum tile ID for visible (BMP-rendered) Subspace tiles — IDs 1..190 are visible. */
  public static final int MAX_VISIBLE_TILE = 190;

  /**
   * Compute the block-type base index for a given arena slot. Tile slots run for
   * {@link #TILE_COUNT} contiguous entries starting at the returned base, within the
   * {@link #BLOCK_TYPE_INDEX_SIZE} range.
   *
   * @param arenaIndex zero-based arena slot
   * @return {@code TILE_TYPE_BASE + arenaIndex * TILE_COUNT}
   */
  public static int arenaTileBase(final int arenaIndex) {
    return TILE_TYPE_BASE + arenaIndex * TILE_COUNT;
  }

  /**
   * Bin neighbor radius for the fine physics index ({@code LEAF_GRID}, 32-unit cells). A radius of
   * (1, 1, 1) means contact-gen scans the body's own bin plus the 3×3×3 of cells around it. This
   * is the historical default; passing {@code null} to {@code MPhysSystem} resolves to the same.
   */
  public static final Vec3i PHYSICS_BIN_RADIUS = new Vec3i(1, 1, 1);

  /**
   * Bin neighbor radius for the coarse static-only physics index ({@code TILE_GRID}, 1024-unit
   * cells). Zero radius assumes every {@code LargeObject}-tagged entity fits within one coarse
   * cell; raise to (1, 0, 1) only if a large structure is authored such that its extent crosses a
   * coarse cell boundary (mphys bins by body center, so a body straddling two coarse cells is
   * indexed in only one without the radius bump).
   */
  public static final Vec3i LARGE_BIN_RADIUS = new Vec3i(0, 0, 0);

  /** World-space Y for the gameplay plane (game is 2D on X/Z); drivers clamp bodies here each tick. */
  public static final double GAMEPLAY_Y = 1.0;

  /**
   * Default gravity used for the physics simulation. - Changed from 0,-10,0 to 0,0,0 for ZERO
   * gravity (space sim)
   */
  public static final Vec3d NO_GRAVITY = new Vec3d(0, 0, 0);
  public static final float MAX_OBJECT_RADIUS = 32; // with big ships, it needs to be bumped.
  public static final int POSITION_BIT_COUNT = 18; // with bigger radius, we need more bits.
  /**
   * Defines how many network message bits to encode the elements of rotation fields. Given that
   * rotation Quaternion values are always between -1 and 1, 12 bits seems sufficient based on
   * ultimate resolution and visual testing.
   */
  public static final QuatBits ROTATION_BITS = new QuatBits(12);
  /**
   * Defines the 3D zone radius around which updates will be sent. The player is always considered
   * to be in the 'center' and this radius defines how many zones in each direction are included in
   * the view. (A 2D game might only define x,z and leave y as 0.) So a radius of 1 in x means that
   * the player can see one zone to either side of their current zone. A total zone radius of (1, 1,
   * 1) means the player can see a total of 27 zones including the zone they are in.
   */
  public static final Vec3i ZONE_RADIUS = new Vec3i(2, 0, 2);
  /** 3D zone grid; larger grid favors fewer multi-zone objects at the cost of more objects per zone. */
  public static final ZoneGrid ZONE_GRID = new ZoneGrid(WorldGrids.LEAF_SIZE, 0, WorldGrids.LEAF_SIZE);
  /**
   * Defines how many network message bits to encode the elements of position fields. This will be a
   * function of the grid size and resolution desired. Keep in mind that objects can be in a zone
   * even if their raw position is not in that zone because their radius may overlap that zone. So
   * the proper range needs to account for this overlap or there will be odd position clipping at
   * the borders as objects cross zone boundaries.
   */
  public static final Vec3Bits POSITION_BITS =
      new Vec3Bits(-MAX_OBJECT_RADIUS, WorldGrids.LEAF_SIZE + MAX_OBJECT_RADIUS, POSITION_BIT_COUNT);
  /** Object update protocol: 8-bit zone IDs (supports zone-radius up to (3,3,2)), 64-bit object IDs, {@link #POSITION_BITS}, {@link #ROTATION_BITS}. */
  public static final ObjectStateProtocol OBJECT_PROTOCOL =
      new ObjectStateProtocol(8, 64, POSITION_BITS, ROTATION_BITS);
}
