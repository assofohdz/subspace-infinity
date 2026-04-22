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

import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.Direction;
import com.simsilica.mblock.geom.BlockFactory;
import com.simsilica.mblock.geom.BoundaryShape;
import com.simsilica.mblock.geom.BoundaryShapes;
import com.simsilica.mblock.geom.DefaultPartFactory;
import com.simsilica.mblock.geom.GeomPart;
import com.simsilica.mblock.geom.GeomPartBuffer;
import com.simsilica.mblock.geom.MaterialType;
import com.simsilica.mblock.geom.PartFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A BlockFactory that creates flat tile quads facing up at Y=1. Used for rendering 2D Subspace-style
 * tiles within the Moss block system.
 *
 * <p>The tile is rendered as a single quad on the top face (Direction.Up) positioned at Y=1, making
 * it a flat sprite that can be viewed from above.
 *
 * @author Asser Fahrenholz
 */
public class FlatTileBlockFactory implements BlockFactory {

  static final long serialVersionUID = 42L;
  static Logger log = LoggerFactory.getLogger(FlatTileBlockFactory.class);

  /** Number of columns in the Subspace tileset atlas (19 tiles per row). */
  public static final int TILESET_COLUMNS = 19;

  /** Number of rows in the Subspace tileset atlas (10 rows). */
  public static final int TILESET_ROWS = 10;

  private final PartFactory upFace;
  private final Vec3d min;
  private final Vec3d max;

  /**
   * Creates a FlatTileBlockFactory for a specific tile with pre-computed UV coordinates.
   *
   * @param materialType the material type for the tileset atlas
   * @param tileId the tile index (1-190 for Subspace tiles)
   * @return a new FlatTileBlockFactory with correct atlas UVs
   */
  public static FlatTileBlockFactory createForTile(final MaterialType materialType, final int tileId) {
    // Calculate UV coordinates for this tile in the atlas
    // Subspace tiles are 1-indexed, so subtract 1 for 0-based indexing
    int tileIndex = tileId - 1;
    if (tileIndex < 0) {
      tileIndex = 0;
    }

    int col = tileIndex % TILESET_COLUMNS;
    int row = tileIndex / TILESET_COLUMNS;

    float tileWidth = 1.0f / TILESET_COLUMNS;
    float tileHeight = 1.0f / TILESET_ROWS;

    float u0 = col * tileWidth;
    float v0 = row * tileHeight;
    float u1 = u0 + tileWidth;
    float v1 = v0 + tileHeight;

    return new FlatTileBlockFactory(materialType, u0, v0, u1, v1);
  }

  /**
   * Creates a FlatTileBlockFactory with specific UV coordinates.
   *
   * @param materialType the material type
   * @param u0 left UV coordinate
   * @param v0 top UV coordinate
   * @param u1 right UV coordinate
   * @param v1 bottom UV coordinate
   */
  public FlatTileBlockFactory(
      final MaterialType materialType,
      final float u0,
      final float v0,
      final float u1,
      final float v1) {
    // Create a custom GeomPart with the specified UV coordinates
    GeomPart part = createFlatQuadWithUVs(materialType, u0, v0, u1, v1);
    this.upFace =
        new DefaultPartFactory(BoundaryShapes.UNIT_SQUARE, new Vec3d(0, 0, 0), new Vec3d(1, 0, 1), part);
    this.min = new Vec3d(0, 0, 0);
    this.max = new Vec3d(1, 0, 1);
  }

  /**
   * Creates a GeomPart for a flat UP-facing quad with custom UV coordinates.
   * The quad is positioned at Y=0 (bottom of block unit) so it sits at the cell's world Y position.
   */
  private static GeomPart createFlatQuadWithUVs(
      final MaterialType materialType,
      final float u0,
      final float v0,
      final float u1,
      final float v1) {
    // Create a GeomPart for Direction.Up at Y=0 with custom UVs
    GeomPart part = new GeomPart(materialType, Direction.Up.ordinal(), false);

    // Vertex positions for UP face at Y=0 (bottom of block, flat quad)
    part.setCoords(
        new float[] {
          0, 0, 1, // vertex 0
          1, 0, 1, // vertex 1
          1, 0, 0, // vertex 2
          0, 0, 0 // vertex 3
        });

    // Custom UV coordinates for the atlas tile (rotated 180°: flip both U and V)
    part.setTexCoords(
        new float[] {
          u1, 1 - v0, // vertex 0
          u0, 1 - v0, // vertex 1
          u0, 1 - v1, // vertex 2
          u1, 1 - v1  // vertex 3
        });

    // Triangle indices: 0-1-2, 0-2-3
    part.setIndexes(new short[] {0, 1, 2, 0, 2, 3});

    return part;
  }

  @Override
  public int addGeometryToBuffer(
      final GeomPartBuffer buffer,
      final int i,
      final int j,
      final int k,
      final int xWorld,
      final int yWorld,
      final int zWorld,
      final int sideMask,
      final CellData cells,
      final BlockType type) {

    // Always render the tile quad regardless of sideMask.
    // Tile BlockTypes are null on the server so side masks are always 0.
    // Flat tiles are decorative sprites that should never be face-culled.
    return upFace.addParts(buffer, i, j, k, xWorld, yWorld, zWorld, type, Direction.Up);
  }

  @Override
  public BoundaryShape getShape(final Direction dir) {
    if (dir == Direction.Up) {
      return BoundaryShapes.UNIT_SQUARE;
    }
    return BoundaryShapes.NULL_SHAPE;
  }

  @Override
  public boolean isSolid(final Direction dir) {
    return dir == Direction.Up;
  }

  @Override
  public boolean isSolid() {
    return false;
  }

  @Override
  public double getTransparency(final Direction dir) {
    return dir == Direction.Up ? 0.5 : 1.0;
  }

  @Override
  public boolean isTransparent() {
    return true;
  }

  @Override
  public double getVolume() {
    return 0;
  }

  @Override
  public Vec3d getMin() {
    return min;
  }

  @Override
  public Vec3d getMax() {
    return max;
  }

  @Override
  public BlockFactory rotate(final int dirDelta) {
    return this;
  }
}
