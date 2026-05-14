// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
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
 * {@link BlockFactory} that emits a single flat upward-facing quad — Subspace
 * 2D tile sprite for the Moss block system. The quad sits at the cell's
 * bottom (Y=0 local) so at world Y=2 it renders above the world-Y=1 collision
 * plane.
 *
 * <p>UV math addresses only the inner {@link #TILE_PIXELS}×{@link #TILE_PIXELS}
 * region of each {@link #GUTTER_PIXELS}-padded cell in the loaded atlas. The
 * gutter is a replicate-pad border so trilinear/anisotropic filtering can
 * sample inside a tile without the GPU's 4-texel kernel reaching into the
 * neighbouring tile.
 */
public class FlatTileBlockFactory implements BlockFactory {

  static final long serialVersionUID = 42L;
  static Logger log = LoggerFactory.getLogger(FlatTileBlockFactory.class);

  public static final int TILESET_COLUMNS = 19;
  public static final int TILESET_ROWS = 10;
  public static final int TILE_PIXELS = 16;

  /** Edge-replicated padding around each tile — keeps trilinear/anisotropic filtering from bleeding across tiles. */
  public static final int GUTTER_PIXELS = 2;

  private static final int CELL_PIXELS = TILE_PIXELS + 2 * GUTTER_PIXELS;
  private static final float GUTTER_RATIO = (float) GUTTER_PIXELS / CELL_PIXELS;

  private final PartFactory upFace;
  private final Vec3d min;
  private final Vec3d max;

  /** {@code tileId} is 1-190 (Subspace convention); UVs computed from the atlas layout. */
  public static FlatTileBlockFactory createForTile(final MaterialType materialType, final int tileId) {
    // Calculate UV coordinates for this tile in the atlas
    // Subspace tiles are 1-indexed, so subtract 1 for 0-based indexing
    int tileIndex = tileId - 1;
    if (tileIndex < 0) {
      tileIndex = 0;
    }

    int col = tileIndex % TILESET_COLUMNS;
    int row = tileIndex / TILESET_COLUMNS;

    // Address the inner TILE_PIXELS x TILE_PIXELS region of each padded cell, skipping the
    // GUTTER_PIXELS-wide replicate border on every side. The atlas is laid out as
    // (TILESET_COLUMNS x CELL_PIXELS) wide by (TILESET_ROWS x CELL_PIXELS) tall, so each
    // cell still occupies (1.0 / TILESET_COLUMNS) of U and (1.0 / TILESET_ROWS) of V.
    float cellU = 1.0f / TILESET_COLUMNS;
    float cellV = 1.0f / TILESET_ROWS;
    float u0 = (col + GUTTER_RATIO) * cellU;
    float u1 = (col + 1 - GUTTER_RATIO) * cellU;
    float v0 = (row + GUTTER_RATIO) * cellV;
    float v1 = (row + 1 - GUTTER_RATIO) * cellV;

    return new FlatTileBlockFactory(materialType, u0, v0, u1, v1);
  }

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

  // Quad sits at Y=0 (bottom of block unit) so it lands at the cell's world Y.
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

    // Normals are auto-generated from the direction (Up → (0,1,0)) by
    // GeomPart.setupAlternates() when the MaterialType declares GeomReq.Normals.
    // Calling setNormals() explicitly here conflicts with that path ("Setting
    // normals on a part with indexed normals.") so we let MOSS do it.

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

  // Signature fixed by Moss BlockFactory interface.
  @SuppressWarnings("PMD.ExcessiveParameterList")
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
