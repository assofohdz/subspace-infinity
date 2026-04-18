package infinity.sim;

import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactory;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.MaskUtils;
import com.simsilica.mblock.phys.CellArrayPart;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.BodyMass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a factory that can create cubes. We need ato implement our own since MOSS is not there
 * yet with a real ghost cube.
 */
public class CubeFactory implements ShapeFactory<MBlockShape> {
  static Logger log = LoggerFactory.getLogger(CubeFactory.class);

  public CubeFactory() {
  }

  @Override
  public MBlockShape createShape(String name, double scale, Mass mass) {
    return MBlockShape.createCube(2);
//    if (info.getShapeName(ed).equals(ShapeNames.DOOR)) {
//      return this.createStaticGhostCube(info);
//    }
//    return this.createStaticPhysicalCube(info);
  }

  private MBlockShape createStaticGhostCube(double scale) {
    CellArray cells = new CellArray(1, 1, 1);
    cells.setCell(0, 0, 0, 1);
    MaskUtils.calculateSideMasks(cells);
    CellArrayPart ghostBlock =
        new CellArrayPart(
            new Vec3d(),
            CellArrayPart.Type.Blocks,
            cells,
            1.0,
            scale,
            BodyMass.createSimple(0.0, null, scale));
    return new MBlockShape(ghostBlock);
  }

  private MBlockShape createStaticPhysicalCube(double scale) {
    CellArray cells = new CellArray(1, 1, 1);
    cells.setCell(0, 0, 0, 1);
    MaskUtils.calculateSideMasks(cells);
    return MBlockShape.createShape(cells, scale / 2.0, 0.0);
  }
}
