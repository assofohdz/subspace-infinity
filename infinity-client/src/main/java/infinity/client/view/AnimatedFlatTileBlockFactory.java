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

/**
 * Flat upward-facing quad with full [0,1] UVs — atlas-frame scrolling happens
 * in the AnimateMultilineSprite shader via g_Time, so the mesh stays static
 * and animation is free per render. Sibling of {@link FlatTileBlockFactory},
 * which carves a single atlas cell into the vertex UVs.
 *
 * <p>The {@code scale} constructor parameter sets the quad's X×Z extent. The
 * quad is anchored at the cell's local origin and extends to
 * {@code (scale, 0, scale)} — so a {@code scale=2} block at world cell
 * {@code (X, 1, Z)} renders from world {@code (X, 1, Z)} to {@code (X+2, 1, Z+2)},
 * spilling into the +X / +Z neighbour cells. Collision still uses the single
 * 1×1 cube collider, so callers that need a larger collision footprint must
 * write extra {@code INVISIBLE_BLOCK_TYPE} cells alongside (see how
 * {@code LegacyMapProjector} handles {@code VIE_ASTEROID_MEDIUM}).
 */
public class AnimatedFlatTileBlockFactory implements BlockFactory {

  static final long serialVersionUID = 43L;

  private final PartFactory upFace;
  private final Vec3d min;
  private final Vec3d max;

  public AnimatedFlatTileBlockFactory(final MaterialType materialType) {
    this(materialType, 1.0f);
  }

  public AnimatedFlatTileBlockFactory(final MaterialType materialType, final float scale) {
    final GeomPart part = createFlatQuadFullUv(materialType, scale);
    this.upFace =
        new DefaultPartFactory(
            BoundaryShapes.UNIT_SQUARE, new Vec3d(0, 0, 0), new Vec3d(scale, 0, scale), part);
    this.min = new Vec3d(0, 0, 0);
    this.max = new Vec3d(scale, 0, scale);
  }

  private static GeomPart createFlatQuadFullUv(final MaterialType materialType, final float scale) {
    final GeomPart part = new GeomPart(materialType, Direction.Up.ordinal(), false);
    part.setCoords(
        new float[] {
          0,     0, scale,
          scale, 0, scale,
          scale, 0, 0,
          0,     0, 0
        });
    // Full [0,1] UV — the AnimateMultilineSprite vert shader divides by
    // numTilesX/numTilesY and adds the frame offset itself. Match the
    // 180-degree rotation FlatTileBlockFactory applies.
    part.setTexCoords(
        new float[] {
          1, 1,
          0, 1,
          0, 0,
          1, 0
        });
    part.setIndexes(new short[] {0, 1, 2, 0, 2, 3});
    return part;
  }

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
