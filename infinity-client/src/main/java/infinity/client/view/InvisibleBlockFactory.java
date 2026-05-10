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
import com.simsilica.mblock.geom.GeomPartBuffer;

/**
 * A BlockFactory that produces no visible geometry but maintains a solid collision shape.
 * Used for physics-only blocks that should not render.
 *
 * @author Asser Fahrenholz
 */
public class InvisibleBlockFactory implements BlockFactory {

  static final long serialVersionUID = 42L;

  private static final InvisibleBlockFactory INSTANCE = new InvisibleBlockFactory();

  private final Vec3d min = new Vec3d(0, 0, 0);
  private final Vec3d max = new Vec3d(1, 1, 1);

  private InvisibleBlockFactory() {}

  public static InvisibleBlockFactory getInstance() {
    return INSTANCE;
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
    // Return 0 - no geometry to render
    return 0;
  }

  @Override
  public BoundaryShape getShape(final Direction dir) {
    // Solid cube boundary for physics
    return BoundaryShapes.UNIT_SQUARE;
  }

  @Override
  public boolean isSolid(final Direction dir) {
    return true;
  }

  @Override
  public boolean isSolid() {
    return true;
  }

  @Override
  public double getTransparency(final Direction dir) {
    return 0; // Fully opaque for physics purposes
  }

  @Override
  public boolean isTransparent() {
    return false;
  }

  @Override
  public double getVolume() {
    return 1; // Full cube volume for physics
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
