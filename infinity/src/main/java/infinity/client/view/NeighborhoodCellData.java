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
package infinity.client.view;

import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.Direction;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.LeafId;
import com.simsilica.mworld.LeafInfo;
import com.simsilica.mworld.World;

/**
 * Read-only {@link CellData} view spanning the center leaf plus its 8 horizontal
 * neighbors. Coordinates use the extended neighborhood frame:
 * <pre>
 *   x in [-SIZE .. 2*SIZE)   (center leaf occupies [0 .. SIZE))
 *   y in [0 .. SIZE)
 *   z in [-SIZE .. 2*SIZE)
 * </pre>
 * For a coord outside the 3×3 neighborhood or in an unloaded / empty leaf, returns 0
 * (empty cell) or the provided default.
 *
 * <p>Fed into {@code LightUtils.recalculateLighting} so that emitter flood-fill can
 * seed from and propagate into the 8 adjacent leaves, eliminating the hard light
 * drop at leaf boundaries. Vertical neighbors aren't needed because Subspace is
 * effectively 2D — all emitter and wall cells sit on the same Y layer.
 */
public class NeighborhoodCellData implements CellData {

  private static final int SIZE = LeafInfo.SIZE;

  private final World world;
  private final Vec3i centerOrigin;
  private final LeafData[][] neighbors = new LeafData[3][3];
  private final boolean[][] loaded = new boolean[3][3];

  public NeighborhoodCellData(final World world, final LeafId centerLeafId) {
    this.world = world;
    this.centerOrigin = centerLeafId.getWorld(null);
    neighbors[1][1] = world.getLeaf(centerLeafId);
    loaded[1][1] = true;
  }

  private LeafData leafFor(final int xExtended, final int zExtended) {
    // Map extended coord to 3×3 index: [-SIZE..-1]→0, [0..SIZE-1]→1, [SIZE..2*SIZE-1]→2
    final int i = (xExtended + SIZE) / SIZE;
    final int j = (zExtended + SIZE) / SIZE;
    if (i < 0 || i > 2 || j < 0 || j > 2) {
      return null;
    }
    if (!loaded[i][j]) {
      final int worldX = centerOrigin.x + (i - 1) * SIZE;
      final int worldZ = centerOrigin.z + (j - 1) * SIZE;
      neighbors[i][j] = world.getLeaf(LeafId.fromWorld(worldX, centerOrigin.y, worldZ));
      loaded[i][j] = true;
    }
    return neighbors[i][j];
  }

  private static int mod(final int v) {
    // Positive-modulo that maps extended coords to leaf-local [0..SIZE-1].
    final int r = v % SIZE;
    return r < 0 ? r + SIZE : r;
  }

  @Override
  public int getCell(final int x, final int y, final int z) {
    return getCell(x, y, z, 0);
  }

  @Override
  public int getCell(final int x, final int y, final int z, final int defaultValue) {
    if (y < 0 || y >= SIZE) {
      return defaultValue;
    }
    final LeafData leaf = leafFor(x, z);
    if (leaf == null || leaf.isEmpty()) {
      return 0;
    }
    return leaf.getCell(mod(x), y, mod(z));
  }

  @Override
  public int getCell(final int x, final int y, final int z, final Direction dir,
      final int defaultValue) {
    final Vec3i v = dir.getVec3i();
    return getCell(x + v.x, y + v.y, z + v.z, defaultValue);
  }

  @Override
  public void setCell(final int x, final int y, final int z, final int value) {
    throw new UnsupportedOperationException("NeighborhoodCellData is read-only");
  }
}
