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
import com.simsilica.mblock.geom.GeomPart;
import com.simsilica.mblock.geom.GeomPartBuffer;
import com.simsilica.mblock.geom.MaterialType;

/** Half-size cube hanging below cell origin (Y: -1.0..-0.5) so at cell Y=2 above a wall at Y=1 its base touches the wall. */
public class LanternBlockFactory implements BlockFactory {

  static final long serialVersionUID = 42L;

  private static final float X0 = 0.25f;
  private static final float X1 = 0.75f;
  private static final float Y0 = -1.0f;
  private static final float Y1 = -0.5f;
  private static final float Z0 = 0.25f;
  private static final float Z1 = 0.75f;

  private final Vec3d min = new Vec3d(X0, Y0, Z0);
  private final Vec3d max = new Vec3d(X1, Y1, Z1);
  private final GeomPart[] faces = new GeomPart[6];

  public LanternBlockFactory(final MaterialType materialType) {
    faces[Direction.Up.ordinal()] = buildFace(materialType, Direction.Up);
    faces[Direction.Down.ordinal()] = buildFace(materialType, Direction.Down);
    faces[Direction.North.ordinal()] = buildFace(materialType, Direction.North);
    faces[Direction.South.ordinal()] = buildFace(materialType, Direction.South);
    faces[Direction.East.ordinal()] = buildFace(materialType, Direction.East);
    faces[Direction.West.ordinal()] = buildFace(materialType, Direction.West);
  }

  private static GeomPart buildFace(final MaterialType mt, final Direction dir) {
    GeomPart part = new GeomPart(mt, dir.ordinal(), false);
    switch (dir) {
      case Up: // +Y face, CCW viewed from +Y
        part.setCoords(new float[] {X0, Y1, Z1, X1, Y1, Z1, X1, Y1, Z0, X0, Y1, Z0});
        break;
      case Down: // -Y face, CCW viewed from -Y
        part.setCoords(new float[] {X0, Y0, Z0, X1, Y0, Z0, X1, Y0, Z1, X0, Y0, Z1});
        break;
      case North: // -Z face (looking from -Z)
        part.setCoords(new float[] {X1, Y0, Z0, X0, Y0, Z0, X0, Y1, Z0, X1, Y1, Z0});
        break;
      case South: // +Z face
        part.setCoords(new float[] {X0, Y0, Z1, X1, Y0, Z1, X1, Y1, Z1, X0, Y1, Z1});
        break;
      case East: // +X face
        part.setCoords(new float[] {X1, Y0, Z1, X1, Y0, Z0, X1, Y1, Z0, X1, Y1, Z1});
        break;
      case West: // -X face
        part.setCoords(new float[] {X0, Y0, Z0, X0, Y0, Z1, X0, Y1, Z1, X0, Y1, Z0});
        break;
      default:
        break;
    }
    part.setTexCoords(new float[] {0, 0, 1, 0, 1, 1, 0, 1});
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
    int n = 0;
    for (GeomPart p : faces) {
      buffer.addPart(i, j, k, p);
      n++;
    }
    return n;
  }

  @Override
  public BoundaryShape getShape(final Direction dir) {
    return BoundaryShapes.NULL_SHAPE;
  }

  @Override
  public boolean isSolid(final Direction dir) {
    return false;
  }

  @Override
  public boolean isSolid() {
    return false;
  }

  @Override
  public double getTransparency(final Direction dir) {
    return 1;
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
